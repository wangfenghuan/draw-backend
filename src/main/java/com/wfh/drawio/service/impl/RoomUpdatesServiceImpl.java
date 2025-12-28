package com.wfh.drawio.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wfh.drawio.model.entity.RoomUpdates;
import com.wfh.drawio.service.RoomUpdatesService;
import com.wfh.drawio.mapper.RoomUpdatesMapper;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
* @author fenghuanwang
* @description 针对表【room_updates(协同编辑增量表)】的数据库操作Service实现
* @createDate 2025-12-27 15:51:27
*/
@Service
public class RoomUpdatesServiceImpl extends ServiceImpl<RoomUpdatesMapper, RoomUpdates>
    implements RoomUpdatesService{


    @Resource
    private RoomUpdatesMapper updatesMapper;

    @Override
    @Async
    public void cleanOldUpdates(Long roomId) {
        // 保留最近10分钟的增量
        LocalDateTime safeTime = LocalDateTime.now().minusMinutes(10);

        // DELETE FROM room_updates WHERE room_name = ? AND created_at < ?
        updatesMapper.deleteByRoomAndTimeBefore(roomId, safeTime);
    }
}




