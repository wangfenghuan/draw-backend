package com.wfh.drawio.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.mapper.DiagramRoomMapper;
import com.wfh.drawio.model.dto.room.*;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.entity.DiagramRoom;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.vo.DiagramVO;
import com.wfh.drawio.model.vo.RoomVO;
import com.wfh.drawio.service.DiagramRoomService;
import com.wfh.drawio.service.DiagramService;
import com.wfh.drawio.service.SpaceService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * @Title: RoomController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2025/12/28 11:05
 * @description:
 */
@RestController
@RequestMapping("/room")
public class RoomController {


    @Resource
    private UserService userService;

    @Resource
    private DiagramRoomService roomService;


    @Resource
    private DiagramRoomMapper roomMapper;

    @Resource
    private SpaceService spaceService;

    @Resource
    @Lazy
    private DiagramService diagramService;

    /**
     * 保存图表数据(协同编辑用)
     * @param roomId
     * @param encryptedData
     */
    @PostMapping("/{roomId}/save")
    @PreAuthorize("@roomSecurityService.hasRoomAuthority(#roomId, 'room:diagram:edit') or hasAuthority('admin')")
    public BaseResponse<Boolean> save(@PathVariable Long roomId, @RequestBody byte[] encryptedData, HttpServletRequest request) {
        // 查询房间信息
        DiagramRoom room = roomService.getById(roomId);
        ThrowUtils.throwIf(room == null, ErrorCode.NOT_FOUND_ERROR, "房间不存在");

        // 注解已做房间权限校验
        DiagramRoom diagramRoom = new DiagramRoom();
        diagramRoom.setId(roomId);
        diagramRoom.setEncryptedData(encryptedData);
        // 简单的覆盖保存
        if (roomMapper.updateById(diagramRoom) == 0) {
            roomMapper.insert(diagramRoom);
        }
        return ResultUtils.success(true);
    }

    /**
     * 根据ID获取房间内的图表详情
     * 获取指定图表的详细信息（封装类）
     *
     * @param diagramId 图表ID
     * @param roomId 房间id
     * @param request HTTP请求
     * @return 图表详情（封装类）
     */
    @GetMapping("/getDiagram")
    @PreAuthorize("@roomSecurityService.hasAnyRoomAuthority(#roomId, {'room:diagram:view', 'room:diagram:edit'}) or hasAuthority('admin')")
    @Operation(summary = "获取房间内的图表详情",
            description = """
                    根据ID获取图表的详细信息。

                    **权限要求：**
                    - 需要登录
                    - 协作房间：需要有房间查看权限
                    - 管理员可以查看所有房间

                    **返回内容：**
                    - 图表基本信息（ID、名称、描述等）
                    - 文件URL（svgUrl、pictureUrl）
                    - 文件大小（svgSize、pngSize、picSize）
                    - 所属空间信息（spaceId）
                    """)
    public BaseResponse<DiagramVO> getRoomDiagramVO(@RequestParam long diagramId, @RequestParam long roomId, HttpServletRequest request) {
        ThrowUtils.throwIf(diagramId <= 0 || roomId <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Diagram diagram = diagramService.getById(diagramId);
        ThrowUtils.throwIf(diagram == null, ErrorCode.NOT_FOUND_ERROR);
        DiagramRoom room = roomService.getById(roomId);
        ThrowUtils.throwIf(room == null, ErrorCode.NOT_FOUND_ERROR, "房间不存在");

        // 注解已做房间权限校验
        // 获取封装类
        return ResultUtils.success(diagramService.getDiagramVO(diagram, request));
    }

    /**
     * 创建房间
     * @param roomAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @Operation(summary = "创建房间")
    public BaseResponse<Long> addRoom(@RequestBody RoomAddRequest roomAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(roomAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);

        // 校验空间权限
        roomService.validateSpacePermission(roomAddRequest.getSpaceId(), loginUser);

        // 判断房间是否存在，不存在才创建
        LambdaQueryWrapper<DiagramRoom> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiagramRoom::getDiagramId, roomAddRequest.getDiagramId());
        DiagramRoom room = roomService.getOne(queryWrapper);
        if (room == null){
            DiagramRoom newRoom = new DiagramRoom();
            BeanUtils.copyProperties(roomAddRequest, newRoom);
            newRoom.setOwnerId(loginUser.getId());

            // 写入数据库
            boolean result = roomService.save(newRoom);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
            long newRoomId = newRoom.getId();
            return ResultUtils.success(newRoomId);
        }
        // 房间存在，就返回房间ID
        // 返回新写入的数据 id
        return ResultUtils.success(room.getId());
    }


    /**
     * 删除房间
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @PreAuthorize("@roomSecurityService.hasRoomAuthority(#deleteRequest.id, 'room:user:manage') or hasAuthority('admin')")
    @Operation(summary = "删除房间",
            description = """
                    删除指定的协作房间。

                    **权限要求：**
                    - 需要登录
                    - 协作房间：需要有房间用户管理权限
                    - 管理员可以删除任何房间
                    """)
    public BaseResponse<Boolean> deleteDiagramRoom(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        DiagramRoom oldDiagramRoom = roomService.getById(id);
        ThrowUtils.throwIf(oldDiagramRoom == null, ErrorCode.NOT_FOUND_ERROR);

        // 注解已做房间权限校验
        // 操作数据库
        boolean result = roomService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新房间（仅管理员可用）
     *
     * @param roomUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "更新房间信息（仅管理员可用）")
    public BaseResponse<Boolean> updateDiagramRoom(@RequestBody RoomUpdateRequest roomUpdateRequest) {
        if (roomUpdateRequest == null || roomUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        DiagramRoom room = new DiagramRoom();
        BeanUtils.copyProperties(roomUpdateRequest, room);
        // 数据校验
        roomService.validRoom(room, false);
        // 判断是否存在
        long id = roomUpdateRequest.getId();
        DiagramRoom oldDiagramRoom = roomService.getById(id);
        ThrowUtils.throwIf(oldDiagramRoom == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = roomService.updateById(room);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取房间（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据 id 获取房间（封装类）")
    public BaseResponse<RoomVO> getDiagramRoomVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        DiagramRoom room = roomService.getById(id);
        ThrowUtils.throwIf(room == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        User loginUser = userService.getLoginUser(request);
        spaceService.checkRoomAuth(loginUser, room);
        // 获取封装类
        RoomVO diagramRoomVO = roomService.getDiagramRoomVO(room, request);
        return ResultUtils.success(diagramRoomVO);
    }

    /**
     * 分页获取房间列表（仅管理员可用）
     *
     * @param roomQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "分页获取房间列表（仅管理员可用）")
    public BaseResponse<Page<DiagramRoom>> listDiagramRoomByPage(@RequestBody RoomQueryRequest roomQueryRequest) {
        long current = roomQueryRequest.getCurrent();
        long size = roomQueryRequest.getPageSize();
        // 查询数据库
        Page<DiagramRoom> roomPage = roomService.page(new Page<>(current, size),
                roomService.getQueryWrapper(roomQueryRequest));
        return ResultUtils.success(roomPage);
    }

    /**
     * 分页获取房间列表（封装类）
     *
     * @param roomQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页获取房间列表（封装类）")
    public BaseResponse<Page<RoomVO>> listDiagramRoomVOByPage(@RequestBody RoomQueryRequest roomQueryRequest,
                                                             HttpServletRequest request) {
        long current = roomQueryRequest.getCurrent();
        long size = roomQueryRequest.getPageSize();
        Long spaceId = roomQueryRequest.getSpaceId();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 如果有空间ID，需要校验空间权限
        if (spaceId != null) {
            User loginUser = userService.getLoginUser(request);
            roomService.validateSpacePermission(spaceId, loginUser);
        }

        // 查询数据库
        Page<DiagramRoom> roomPage = roomService.page(new Page<>(current, size),
                roomService.getQueryWrapper(roomQueryRequest));
        // 获取封装类
        return ResultUtils.success(roomService.getDiagramRoomVOPage(roomPage, request));
    }

    /**
     * 分页获取当前登录用户创建的房间列表
     *
     * @param roomQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    @Operation(summary = "分页获取当前登录用户创建的房间列表")
    public BaseResponse<Page<RoomVO>> listMyDiagramRoomVOByPage(@RequestBody RoomQueryRequest roomQueryRequest,
                                                               HttpServletRequest request) {
        ThrowUtils.throwIf(roomQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        roomQueryRequest.setOwerId(loginUser.getId());
        long current = roomQueryRequest.getCurrent();
        long size = roomQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<DiagramRoom> roomPage = roomService.page(new Page<>(current, size),
                roomService.getQueryWrapper(roomQueryRequest));
        // 获取封装类
        return ResultUtils.success(roomService.getDiagramRoomVOPage(roomPage, request));
    }

    /**
     * 编辑房间（给用户使用）
     *
     * @param roomEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @Operation(summary = "编辑房间（给用户使用）")
    @PreAuthorize("@roomSecurityService.hasAnyRoomAuthority(#roomEditRequest.id, {'room:diagram:view', 'room:diagram:edit'}) or hasAuthority('admin')")
    public BaseResponse<Boolean> editDiagramRoom(@RequestBody RoomEditRequest roomEditRequest, HttpServletRequest request) {
        if (roomEditRequest == null || roomEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        DiagramRoom room = new DiagramRoom();
        BeanUtils.copyProperties(roomEditRequest, room);
        // 数据校验
        roomService.validRoom(room, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = roomEditRequest.getId();
        DiagramRoom oldDiagramRoom = roomService.getById(id);
        ThrowUtils.throwIf(oldDiagramRoom == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        spaceService.checkRoomAuth(loginUser, oldDiagramRoom);
        // 操作数据库
        boolean result = roomService.updateById(room);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 修改房间访问地址
     * @param roomUrlEditRequest
     * @param request
     * @return
     */
    @PostMapping("/updateRoomUrl")
    @Operation(summary = "修改房间访问地址")
    public BaseResponse<Boolean> updateRoomUrl(@RequestBody RoomUrlEditRequest roomUrlEditRequest, HttpServletRequest request) {
        if (roomUrlEditRequest == null || roomUrlEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        DiagramRoom room = new DiagramRoom();
        BeanUtils.copyProperties(roomUrlEditRequest, room);
        // 数据校验
        roomService.validRoom(room, false);
        // 判断是否存在
        long id = roomUrlEditRequest.getId();
        DiagramRoom oldDiagramRoom = roomService.getById(id);
        ThrowUtils.throwIf(oldDiagramRoom == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = roomService.updateById(room);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

}
