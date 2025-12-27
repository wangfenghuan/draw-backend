package com.wfh.drawio.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.model.entity.RoomSnapshots;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
* @author fenghuanwang
* @description 针对表【room_snapshots(协同编辑快照表)】的数据库操作Mapper
* @createDate 2025-12-27 15:51:27
* @Entity com.wfh.drawio.model.entity.RoomSnapshots
*/
public interface RoomSnapshotsMapper extends BaseMapper<RoomSnapshots> {

    /**
     * 找到一条最新的快照
     * @param roomName
     * @return
     */
    default RoomSnapshots selectLatestByRoom(String roomName){
        return selectOne(new LambdaQueryWrapper<RoomSnapshots>()
                .eq(RoomSnapshots::getRoomName, roomName)
                .orderByDesc(RoomSnapshots::getId)
                .last("LIMIT 1"));
    }
}




