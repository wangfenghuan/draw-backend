package com.wfh.drawio.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wfh.drawio.model.entity.RoomSnapshots;
import com.wfh.drawio.service.RoomSnapshotsService;
import com.wfh.drawio.mapper.RoomSnapshotsMapper;
import org.springframework.stereotype.Service;

/**
* @author fenghuanwang
* @description 针对表【room_snapshots(协同编辑快照表)】的数据库操作Service实现
* @createDate 2025-12-27 15:51:27
*/
@Service
public class RoomSnapshotsServiceImpl extends ServiceImpl<RoomSnapshotsMapper, RoomSnapshots>
    implements RoomSnapshotsService{

    @Override
    public void cleanOldSnapshots(Long roomId) {
        // 1. 查询该房间所有快照ID，按 ID 倒序 (最新的在前面)
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoomSnapshots> wrapper = 
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.eq(RoomSnapshots::getRoomId, roomId)
               .select(RoomSnapshots::getId)
               .orderByDesc(RoomSnapshots::getId);
        
        java.util.List<RoomSnapshots> list = this.list(wrapper);
        
        if (list.size() <= 20) {
            return;
        }
        
        // 2. 获取需要删除的 ID 列表 (从第 21 个开始)
        java.util.List<Long> deleteIds = list.subList(20, list.size()).stream()
                                             .map(RoomSnapshots::getId)
                                             .collect(java.util.stream.Collectors.toList());
        
        // 3. 批量删除
        if (!deleteIds.isEmpty()) {
            this.removeBatchByIds(deleteIds);
        }
    }
}




