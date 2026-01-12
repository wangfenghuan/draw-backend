package com.wfh.drawio.ws.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wfh.drawio.mapper.RoomUpdatesMapper;
import com.wfh.drawio.model.entity.RoomUpdates;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * @Title: RoomUpdateBatchService
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ws.service
 * @Date 2025/12/27 16:20
 * @description:
 */
@Slf4j
@Service
public class RoomUpdateBatchService extends ServiceImpl<RoomUpdatesMapper, RoomUpdates> {

    /**
     * 内存阻塞队列
     */
    private final BlockingQueue<RoomUpdates> queue = new LinkedBlockingDeque<>(10000);

    /**
     * 运行状态标记
     */
    private volatile boolean isRunning = true;

    /**
     * 批量写入阈值
     */
    private static final int BATCH_SIZE = 200;
    private static final int WAIT_TIME_MS = 500;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(r ->
            new Thread(r, "Batch-Consumer"));

    /**
     * 更新
     * @param update
     */
    public void addUpdate(RoomUpdates update) {
        if (!queue.offer(update)) {
            log.info("写入队列已满，可能是数据库写入过慢，本次更新丢失！Room: {}", update);
        }
    }


    @PostConstruct
    public void init() {
       executorService.execute(this::batchProcessLoop);
    }

    protected void batchProcessLoop(){
        while (isRunning){
            try {
                // 存放从队列中取出的数据
                List<RoomUpdates> buffer = new ArrayList<>();
                // 尝试从队列中取出方法
                int count = queue.drainTo(buffer, BATCH_SIZE);
                if (count == 0){
                    TimeUnit.MILLISECONDS.sleep(WAIT_TIME_MS);
                    continue;
                }
                // 执行批量插入
                this.saveBatch(buffer);
                log.info("异步批量写入完成，条数:{}", count);
            }catch (Exception e){
                log.error("批量写入异常", e);
            }
        }
    }

    @PreDestroy
    public void destroy() {
        isRunning = false;
        log.info("应用关闭，正在处理剩余的 {} 条数据...", queue.size());

        List<RoomUpdates> buffer = new ArrayList<>();
        queue.drainTo(buffer);
        if (!buffer.isEmpty()) {
            this.saveBatch(buffer);
        }
        log.info("剩余数据处理完毕");
    }
}
