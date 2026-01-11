package com.wfh.drawio.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.model.dto.room.RoomQueryRequest;
import com.wfh.drawio.model.entity.DiagramRoom;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.vo.RoomVO;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author fenghuanwang
* @description 针对表【diagram_room】的数据库操作Service
* @createDate 2025-12-28 11:00:22
*/
public interface DiagramRoomService extends IService<DiagramRoom> {

    /**
     * 获取查询包装器
     * @param roomQueryRequest
     * @return
     */
    Wrapper<DiagramRoom> getQueryWrapper(RoomQueryRequest roomQueryRequest);

    /**
     * 获取分页包装类
     * @param roomPage
     * @param request
     * @return
     */
    Page<RoomVO> getDiagramRoomVOPage(Page<DiagramRoom> roomPage, HttpServletRequest request);

    /**
     * 校验数据
     * @param room
     * @param b
     */
    void validRoom(DiagramRoom room, boolean b);


    /**
     * 获取房间封装类
     * @param room
     * @param request
     * @return
     */
    RoomVO getDiagramRoomVO(DiagramRoom room, HttpServletRequest request);

    /**
     * 校验空间权限
     * 如果有空间ID，需要校验空间是否存在以及用户是否有权限
     *
     * @param spaceId 空间ID
     * @param loginUser 登录用户
     */
    void validateSpacePermission(Long spaceId, User loginUser);
}
