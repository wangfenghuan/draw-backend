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

}




