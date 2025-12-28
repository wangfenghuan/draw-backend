package com.wfh.drawio.service;

import com.wfh.drawio.model.entity.RoomUpdates;
import com.baomidou.mybatisplus.extension.service.IService;

/**
* @author fenghuanwang
* @description 针对表【room_updates(协同编辑增量表)】的数据库操作Service
* @createDate 2025-12-27 15:51:27
*/
public interface RoomUpdatesService extends IService<RoomUpdates> {

    /**
     * 清理旧的增量数据
     * @param roomId
     */
    void cleanOldUpdates(Long roomId);

}
