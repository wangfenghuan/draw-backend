package com.wfh.drawio.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.model.entity.RoomUpdates;
import com.wfh.drawio.service.RoomUpdatesService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author fenghuanwang
 * @description: 异步持久化任务
 * 负责将 Redis List 中的 Yjs 增量数据搬运到 MySQL
 */
@Slf4j
@Component
public class DataPersistenceTask {


    @Resource
    private RedisTemplate<String, byte[]> bytesRedisTemplate;

    @Resource
    private RoomUpdatesService roomUpdatesService;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private SnapshotCompactionTask snapshotCompactionTask;

    private static final String KEY_PATTERN = "drawio:updates:*";
    private static final int BATCH_SIZE = 500;
    private static final int COMPACTION_THRESHOLD = 500;

    /**
     * 每 30 秒执行一次
     * 使用 SCAN 命令遍历 Key，避免阻塞 Redis
     */
    @Scheduled(fixedDelay = 30000)
    public void syncRedisToMysql() {
        String lockKey = "lock:data:persistence";
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 尝试获取锁，最多等待 0 秒，锁超时时间 10 分钟
            boolean isLocked = lock.tryLock(0, 10, TimeUnit.MINUTES);
            if (!isLocked) {
                log.warn("⚠️ 数据持久化任务正在执行，跳过本次执行");
                return;
            }

            log.info("🔄 开始执行 Redis->MySQL 数据同步任务...");
            long start = System.currentTimeMillis();
            int totalProcessed = 0;

            // 1. 定义 Scan 选项 (count 1000 表示建议 Redis 每次扫描返回的 key 数量，非严格)
            ScanOptions options = ScanOptions.scanOptions()
                    .match(KEY_PATTERN)
                    .count(1000)
                    .build();

            // 2. 使用 execute 执行 SCAN (避免 RedisTemplate.keys 的 O(N) 阻塞)
            // Cursor 会自动处理 Redis 的游标翻页
            try (Cursor<String> cursor = bytesRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    String key = cursor.next();
                    try {
                        // 处理单个房间的数据
                        totalProcessed += processRoomData(key);
                    } catch (Exception e) {
                        log.error("❌ 处理房间 {} 数据失败", key, e);
                    }
                }
            } catch (Exception e) {
                log.error("❌ 执行 SCAN 失败", e);
            }
            long cost = System.currentTimeMillis() - start;
            log.info("✅ 同步任务结束，耗时 {}ms，共入库 {} 条记录", cost, totalProcessed);

        } catch (Exception e) {
            log.error("❌ 数据持久化任务被中断", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("🔓 释放数据持久化锁");
            }
        }
    }

    /**
     * 处理单个房间的数据搬运
     * 策略：Range(读取) -> Save(入库) -> Trim(删除)
     * @return 入库条数
     */
    private int processRoomData(String key) {
        int processedCount = 0;
        
        // 解析 RoomID
        String roomIdStr = key.substring(key.lastIndexOf(':') + 1);
        Long roomId;
        try {
            roomId = Long.valueOf(roomIdStr);
        } catch (NumberFormatException e) {
            log.warn("⚠️ 发现非法 Key 格式: {}", key);
            return 0;
        }

        // 循环分批处理，直到 List 为空
        while (true) {
            // A. 获取 List 长度
            Long size = bytesRedisTemplate.opsForList().size(key);
            if (size == null || size == 0) {
                // List 为空，直接退出
                // 不主动删除 key，避免并发写入时误删新数据
                // Redis key 会依赖 TTL 自动过期，或者保留 key 等待新数据写入
                break;
            }

            // B. 计算本次要取的范围 (0 到 BATCH_SIZE - 1)
            long end = (size > BATCH_SIZE) ? (BATCH_SIZE - 1) : (size - 1);

            // C. 读取数据 (不会删除 Redis 数据)
            List<byte[]> rawUpdates = bytesRedisTemplate.opsForList().range(key, 0, end);
            
            if (CollectionUtils.isEmpty(rawUpdates)) {
                break;
            }

            // D. 转换为实体列表
            List<RoomUpdates> entities = new ArrayList<>(rawUpdates.size());
            for (byte[] data : rawUpdates) {
                RoomUpdates update = new RoomUpdates();
                update.setRoomId(roomId);
                update.setUpdateData(data);
                entities.add(update);
            }

            // E. MyBatis-Plus 批量插入 (这一步如果失败抛异常，下面 Trim 就不会执行，保证数据不丢)
            boolean success = roomUpdatesService.saveBatch(entities);

            if (success) {
                // F. 安全清理 Redis (Trim)
                // ltrim key start stop -> 保留 start 到 stop 的元素
                // 我们处理了前 (end + 1) 个，所以保留 (end + 1) 到 -1 (最后)
                bytesRedisTemplate.opsForList().trim(key, end + 1, -1);
                
                processedCount += entities.size();
                log.debug("房间 {} 批次入库 {} 条", roomId, entities.size());
            } else {
                log.error("❌ 房间 {} 数据库批量插入失败，跳过清理 Redis，等待下次重试", roomId);
                break; // 停止当前房间处理，防止死循环
            }
        }

        // 检查是否需要触发快照合并
        checkAndTriggerCompaction(roomId);

        return processedCount;
    }

    /**
     * 检查并触发快照合并
     * 如果房间的 updates 数量超过阈值，触发快照合并任务
     *
     * 注意：doCompaction 方法有分布式锁保护，如果锁被占用会立即返回，
     *      不会长时间阻塞持久化任务
     */
    private void checkAndTriggerCompaction(Long roomId) {
        try {
            // 查询该房间的 updates 总数
            long count = roomUpdatesService.count(
                    new LambdaQueryWrapper<RoomUpdates>()
                            .eq(RoomUpdates::getRoomId, roomId)
            );

            if (count >= COMPACTION_THRESHOLD) {
                log.info("🚨 房间 {} 的 updates 数量达到 {} 条，触发快照合并", roomId, count);
                // 触发快照合并（有分布式锁保护，不会重复执行）
                snapshotCompactionTask.doCompaction(roomId);
            } else {
                log.debug("✅ 房间 {} 的 updates 数量为 {}，未达到合并阈值 {}", roomId, count, COMPACTION_THRESHOLD);
            }
        } catch (Exception e) {
            log.error("❌ 检查房间 {} 快照合并条件失败", roomId, e);
        }
    }
}