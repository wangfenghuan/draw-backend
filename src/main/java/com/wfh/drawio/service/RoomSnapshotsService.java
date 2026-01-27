package com.wfh.drawio.service;

import com.wfh.drawio.model.entity.RoomSnapshots;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author fenghuanwang
* @description 针对表【room_snapshots(协同编辑快照表)】的数据库操作Service
* @createDate 2025-12-27 15:51:27
*/
public interface RoomSnapshotsService extends IService<RoomSnapshots> {

    /**
     * 保留最新的 20 个快照，删除其余的
     * @param roomId
     */
    void cleanOldSnapshots(Long roomId);
}
