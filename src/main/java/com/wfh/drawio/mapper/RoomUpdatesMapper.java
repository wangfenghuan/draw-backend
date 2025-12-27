package com.wfh.drawio.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.model.entity.RoomUpdates;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

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

}




