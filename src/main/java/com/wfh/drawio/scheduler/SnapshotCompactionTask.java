package com.wfh.drawio.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.manager.RustFsManager;
import com.wfh.drawio.mapper.RoomSnapshotsMapper;
import com.wfh.drawio.mapper.RoomUpdatesMapper;
import com.wfh.drawio.model.dto.redisdto.CompactionRequest;
import com.wfh.drawio.model.dto.redisdto.CompactionResponse;
import com.wfh.drawio.model.entity.RoomUpdates;
import jakarta.annotation.Resource;
import com.wfh.drawio.model.entity.RoomSnapshots;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class SnapshotCompactionTask {

    @Resource
    private RedissonClient redissonClient;


    @Resource(name = "msgPackRestTemplate")
    private RestTemplate restTemplate;

    @Resource
    private S3Client s3Client;

    @Value("${yjs.merger.url:http://localhost:3000/compact}")
    private String nodeServiceUrl;

    @Resource
    private RoomUpdatesMapper updatesMapper;

    @Resource
    private RoomSnapshotsMapper snapshotsMapper;

    @Value("${rustfs.client.snapshot-bucket}")
    private String bucketName;


    /**
     * 快照合并任务
     * 每天或当 update 数量 > 500 时触发
     * @param roomId 房间ID
     */
    public void doCompaction(Long roomId) {
        String lockKey = "lock:compaction:" + roomId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，最多等待 0 秒，锁超时时间 5 分钟
            boolean isLocked = lock.tryLock(0, 5, TimeUnit.MINUTES);
            if (!isLocked) {
                log.warn("⚠️ 房间 {} 的快照合并任务正在执行，跳过本次执行", roomId);
                return;
            }

            log.info("🚀 开始执行房间 {} 的快照合并任务", roomId);
            long start = System.currentTimeMillis();

            // 1. 准备数据
            // 下载 S3 旧快照
            RoomSnapshots latestSnapshot = snapshotsMapper.selectLatestByRoom(String.valueOf(roomId));
            byte[] baseSnapshot = null;
            if (latestSnapshot != null) {
                log.debug("📥 从 S3 下载房间 {} 的旧快照: {}", roomId, latestSnapshot.getObjectKey());
                ResponseBytes<GetObjectResponse> objectAsBytes = s3Client.getObjectAsBytes(
                        b -> b.bucket(bucketName).key(latestSnapshot.getObjectKey()));
                baseSnapshot = objectAsBytes.asByteArray();
            }

            // 从 MySQL 查出未合并的 Updates
            List<RoomUpdates> updatesEntities = selectUnmergedUpdates(roomId, latestSnapshot);
            if (updatesEntities.isEmpty()) {
                log.info("✅ 房间 {} 没有需要合并的 updates", roomId);
                return;
            }
            log.info("📊 房间 {} 查询到 {} 条未合并的 updates", roomId, updatesEntities.size());
            List<byte[]> updatesBytes = updatesEntities.stream().map(RoomUpdates::getUpdateData).toList();

            // 2. 调用 Node.js 合并 (补全逻辑)
            log.debug("🔄 调用 Node.js 服务进行数据合并，roomId: {}", roomId);
            CompactionRequest req = new CompactionRequest(roomId, baseSnapshot, updatesBytes);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(new MediaType("application", "x-msgpack"));
            HttpEntity<CompactionRequest> entity = new HttpEntity<>(req, headers);
            // 发起 POST 请求
            ResponseEntity<CompactionResponse> response = restTemplate.postForEntity(
                    nodeServiceUrl,
                    entity,
                    CompactionResponse.class
            );

            CompactionResponse body = response.getBody();
            byte[] mergedData;
            if (body != null && body.isSuccess() && body.getMerged() != null) {
                mergedData = body.getMerged();
                log.debug("✅ Node.js 合并成功，数据大小: {} bytes", mergedData.length);
            } else {
                String msg = (body != null) ? body.getMessage() : "Unknown error";
                log.error("❌ Node.js 合并失败: {}", msg);
                throw new RuntimeException("Node.js merge failed: " + msg);
            }

            // 3. 生成新 Key 并上传 S3
            String newKey = String.format("rooms/%d/snapshots/%s_%s.bin",
                    roomId,
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")),
                    UUID.randomUUID().toString().substring(0, 8));
            log.debug("📤 上传新快照到 S3: {}", newKey);
            s3Client.putObject(b -> b.bucket(bucketName).key(newKey), RequestBody.fromBytes(mergedData));

            // 4. 存库 & 清理旧 Updates
            RoomSnapshots newSnap = new RoomSnapshots();
            newSnap.setRoomId(roomId);
            newSnap.setObjectKey(newKey);
            // 记录该快照包含的最后一个 update ID
            Long maxUpdateId = updatesEntities.get(updatesEntities.size() - 1).getId();
            newSnap.setLastUpdateId(maxUpdateId);
            snapshotsMapper.insert(newSnap);
            log.debug("💾 保存新快照记录，lastUpdateId={}", maxUpdateId);

            List<Long> deletedIds = updatesEntities.stream().map(RoomUpdates::getId).toList();
            updatesMapper.deleteBatchIds(deletedIds);
            log.info("🗑️ 清理 {} 条已合并的 updates", deletedIds.size());
            long cost = System.currentTimeMillis() - start;
            log.info("✅ 房间 {} 快照合并完成，耗时 {}ms", roomId, cost);
        }catch (Exception e) {
            log.error("❌ 房间 {} 快照合并任务执行失败", roomId, e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 释放房间 {} 的合并锁", roomId);
            }
        }
    }

    /**
     * 获取未合并的 updates
     * @param roomId 房间ID
     * @param latestSnapshot 上一次的快照对象 (可能为 null)
     * @return 按 ID 正序排列的增量列表
     */
    private List<RoomUpdates> selectUnmergedUpdates(Long roomId, RoomSnapshots latestSnapshot) {

        LambdaQueryWrapper<RoomUpdates> wrapper = new LambdaQueryWrapper<>();

        // 1. 基础条件：只查当前房间
        wrapper.eq(RoomUpdates::getRoomId, roomId);

        // 2. 动态条件：如果有上一次快照，只查该快照之后的增量
        // 使用 lastUpdateId 比较，比时间比较更精确可靠
        if (latestSnapshot != null && latestSnapshot.getLastUpdateId() != null) {
            // 只查询 ID 大于快照中记录的最后 update ID 的记录
            wrapper.gt(RoomUpdates::getId, latestSnapshot.getLastUpdateId());
            log.debug("🔍 查询快照之后的 updates: lastUpdateId={}", latestSnapshot.getLastUpdateId());
        }

        // 3. 排序：必须按 ID 正序 (Yjs 对顺序敏感)
        // 限制数量：防止一次查太多撑爆内存，比如一次最多合并 1000 条
        wrapper.orderByAsc(RoomUpdates::getId);
        wrapper.last("LIMIT 1000");

        return updatesMapper.selectList(wrapper);
    }
}