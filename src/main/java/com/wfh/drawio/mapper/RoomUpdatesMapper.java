package com.wfh.drawio.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.model.entity.RoomUpdates;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.time.LocalDateTime;
import java.util.List;

/**
* @author fenghuanwang
* @description 针对表【room_updates(协同编辑增量表)】的数据库操作Mapper
* @createDate 2025-12-27 15:51:27
* @Entity com.wfh.drawio.model.entity.RoomUpdates
*/
public interface RoomUpdatesMapper extends BaseMapper<RoomUpdates> {

    /**
     * 找到快照之后的增量数据列表
     * @param roomName
     * @param lastUpdateId
     * @return
     */
    default List<RoomUpdates> selectByRoomAndIdAfter(String roomName, long lastUpdateId) {
        return selectList(new LambdaQueryWrapper<RoomUpdates>()
                .eq(RoomUpdates::getRoomName, roomName)
                // id > lastUpdateId
                .gt(RoomUpdates::getId, lastUpdateId)
                // 必须按时间顺序
                .orderByAsc(RoomUpdates::getId));
    }

    /**
     * 删除指定房间之前的记录
     * @param roomId
     * @param safeTime
     * @return
     */
    default int deleteByRoomAndTimeBefore(Long roomId, LocalDateTime safeTime) {
        // 构建删除条件
        LambdaQueryWrapper<RoomUpdates> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(RoomUpdates::getId, roomId)
                // and created_at < ? (lt = less than)
                .lt(RoomUpdates::getCreateTime, safeTime);
        // 调用 BaseMapper 自带的 delete 方法
        return this.delete(wrapper);
    }

}




