package com.wfh.drawio.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.room.RoomQueryRequest;
import com.wfh.drawio.model.entity.DiagramRoom;
import com.wfh.drawio.model.vo.RoomVO;
import com.wfh.drawio.service.DiagramRoomService;
import com.wfh.drawio.mapper.DiagramRoomMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* @author fenghuanwang
* @description 针对表【diagram_room】的数据库操作Service实现
* @createDate 2025-12-28 11:00:22
*/
@Service
public class DiagramRoomServiceImpl extends ServiceImpl<DiagramRoomMapper, DiagramRoom>
    implements DiagramRoomService{

    @Override
    public Wrapper<DiagramRoom> getQueryWrapper(RoomQueryRequest roomQueryRequest) {
        QueryWrapper<DiagramRoom> queryWrapper = new QueryWrapper<>();
        if (roomQueryRequest == null) {
            return queryWrapper;
        }
        Long id = roomQueryRequest.getId();
        String name = roomQueryRequest.getRoomName();
        String searchText = roomQueryRequest.getSearchText();
        Long userId = roomQueryRequest.getOwerId();
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("roomName", searchText).or().like("roomName", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(name), "roomName", name);
        // 精确查询
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "ownerId", userId);
        return queryWrapper;
    }

    @Override
    public Page<RoomVO> getDiagramRoomVOPage(Page<DiagramRoom> roomPage, HttpServletRequest request) {
        List<DiagramRoom> diagramList = roomPage.getRecords();
        Page<RoomVO> diagramVOPage = new Page<>(roomPage.getCurrent(), roomPage.getSize(), roomPage.getTotal());
        if (CollUtil.isEmpty(diagramList)) {
            return diagramVOPage;
        }
        // 对象列表 => 封装对象列表
        List<RoomVO> diagramVOList = diagramList.stream().map(RoomVO::objToVo).collect(Collectors.toList());
        diagramVOPage.setRecords(diagramVOList);
        return diagramVOPage;
    }

    @Override
    public void validRoom(DiagramRoom room, boolean b) {
        ThrowUtils.throwIf(room == null, ErrorCode.PARAMS_ERROR);
        String name = room.getRoomName();
        Long roomId = room.getId();
        Long userId = room.getOwerId();
        // 创建数据时，参数不能为空
        if (b) {
            ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(ObjectUtils.isEmpty(userId), ErrorCode.PARAMS_ERROR);
        }
        // 修改数据时，有参数则校验
        if (StringUtils.isNotBlank(name) || ObjectUtils.isNotEmpty(roomId)) {
            ThrowUtils.throwIf(name.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
    }

    @Override
    public RoomVO getDiagramRoomVO(DiagramRoom room, HttpServletRequest request) {
        // 对象转封装类
        return RoomVO.objToVo(room);
    }
}




