package com.wfh.drawio.controller;

import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.roommember.RoomMemberAddRequest;
import com.wfh.drawio.model.dto.roommember.RoomMemberEditRequest;
import com.wfh.drawio.model.dto.roommember.RoomMemberQueryRequest;
import com.wfh.drawio.model.entity.RoomMember;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.vo.RoomMemberVO;
import com.wfh.drawio.service.DiagramRoomService;
import com.wfh.drawio.service.RoomMemberService;
import com.wfh.drawio.service.RoomRoleService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Title: RoomMemberController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2025/12/28 11:05
 * @description: 房间成员管理接口
 */
@Tag(name = "房间成员管理", description = "协作房间成员的增删改查接口")
@RestController
@RequestMapping("/roomMember")
@Slf4j
public class RoomMemberController {

    @Resource
    private RoomMemberService roomMemberService;

    @Resource
    private UserService userService;

    @Resource
    private RoomRoleService roomRoleService;

    /**
     * 添加成员到房间
     *
     * @param roomMemberAddRequest 房间成员添加请求
     * @param request              HTTP请求
     * @return 新创建的成员记录ID
     */
    @PostMapping("/add")
    @PreAuthorize("@roomSecurityService.hasRoomAuthority(#roomMemberAddRequest.roomId, 'room:user:manage') or hasAuthority('admin')")
    @Operation(summary = "添加成员到房间",
            description = """
                    添加成员到协作房间并设置角色。

                    **权限要求：**
                    - 需要登录
                    - 协作房间：需要有房间用户管理权限
                    - 管理员可以添加成员到任何房间

                    **角色说明：**
                    - diagram_admin：房间管理员，拥有所有权限
                    - diagram_editor：编辑者，可以编辑图表
                    - diagram_viewer：查看者，只能查看图表
                    """)
    public BaseResponse<Long> addRoomMember(@RequestBody RoomMemberAddRequest roomMemberAddRequest,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(roomMemberAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 注解已经做了权限校验
        long id = roomMemberService.addRoomMember(roomMemberAddRequest);
        return ResultUtils.success(id);
    }

    /**
     * 从房间移除成员
     *
     * @param deleteRequest 删除请求（包含成员记录ID）
     * @param request       HTTP请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @Operation(summary = "从房间移除成员",
            description = """
                    从协作房间中移除成员。

                    **权限要求：**
                    - 需要登录
                    - 协作房间：需要有房间用户管理权限
                    - 管理员可以移除任何成员
                    """)
    @PreAuthorize("@roomSecurityService.hasRoomAuthority(#deleteRequest.id, 'room:user:manage') or hasAuthority('admin')")
    public BaseResponse<Boolean> deleteRoomMember(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        RoomMember oldRoomMember = roomMemberService.getById(id);
        ThrowUtils.throwIf(oldRoomMember == null, ErrorCode.NOT_FOUND_ERROR);

        // 手动校验房间权限（因为需要先查询 oldRoomMember 才能知道 roomId）
        User loginUser = userService.getLoginUser(request);
        if (!roomRoleService.hasAuthority(loginUser.getId(), oldRoomMember.getRoomId(), "room:user:manage")
                && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无房间用户管理权限");
        }

        // 操作数据库
        boolean result = roomMemberService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询某个成员在某个房间的信息
     *
     * @param roomMemberQueryRequest 查询请求（包含房间ID和用户ID）
     * @return 房间成员实体类
     */
    @PostMapping("/get")
    @Operation(summary = "查询某个成员在某个房间的信息")
    public BaseResponse<RoomMember> getRoomMember(@RequestBody RoomMemberQueryRequest roomMemberQueryRequest) {
        // 参数校验
        ThrowUtils.throwIf(roomMemberQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long roomId = roomMemberQueryRequest.getRoomId();
        Long userId = roomMemberQueryRequest.getUserId();
        ThrowUtils.throwIf(roomId == null || userId == null, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        RoomMember roomMember = roomMemberService.getOne(
                roomMemberService.getQueryWrapper(roomMemberQueryRequest)
        );
        ThrowUtils.throwIf(roomMember == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(roomMember);
    }

    /**
     * 查询房间成员信息列表
     *
     * @param roomMemberQueryRequest 查询请求
     * @param request                HTTP请求
     * @return 房间成员封装类列表
     */
    @PostMapping("/list")
    @Operation(summary = "查询房间成员信息列表")
    public BaseResponse<List<RoomMemberVO>> listRoomMember(@RequestBody RoomMemberQueryRequest roomMemberQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(roomMemberQueryRequest == null, ErrorCode.PARAMS_ERROR);
        List<RoomMember> roomMemberList = roomMemberService.list(
                roomMemberService.getQueryWrapper(roomMemberQueryRequest)
        );
        return ResultUtils.success(roomMemberService.getRoomMemberVOList(roomMemberList));
    }

    /**
     * 编辑成员信息（设置权限）
     *
     * @param roomMemberEditRequest 成员编辑请求（包含成员ID和新角色）
     * @param request               HTTP请求
     * @return 是否编辑成功
     */
    @PostMapping("/edit")
    @Operation(summary = "编辑成员信息（设置权限）",
            description = """
                    修改房间成员的角色权限。

                    **权限要求：**
                    - 需要登录
                    - 协作房间：需要有房间用户管理权限
                    - 管理员可以修改任何成员的权限
                    """)
    public BaseResponse<Boolean> editRoomMember(@RequestBody RoomMemberEditRequest roomMemberEditRequest,
                                               HttpServletRequest request) {
        if (roomMemberEditRequest == null || roomMemberEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        RoomMember roomMember = new RoomMember();
        BeanUtils.copyProperties(roomMemberEditRequest, roomMember);
        // 数据校验
        roomMemberService.validRoomMember(roomMember, false);
        // 判断是否存在
        long id = roomMemberEditRequest.getId();
        RoomMember oldRoomMember = roomMemberService.getById(id);
        ThrowUtils.throwIf(oldRoomMember == null, ErrorCode.NOT_FOUND_ERROR);

        // 手动校验房间权限（因为需要先查询 oldRoomMember 才能知道 roomId）
        User loginUser = userService.getLoginUser(request);
        if (!roomRoleService.hasAuthority(loginUser.getId(), oldRoomMember.getRoomId(), "room:user:manage")
                && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无房间用户管理权限");
        }

        // 操作数据库
        boolean result = roomMemberService.updateById(roomMember);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询我加入的房间列表
     *
     * @param request HTTP请求
     * @return 房间成员封装类列表
     */
    @PostMapping("/list/my")
    @Operation(summary = "查询我加入的房间列表")
    public BaseResponse<List<RoomMemberVO>> listMyRooms(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        RoomMemberQueryRequest roomMemberQueryRequest = new RoomMemberQueryRequest();
        roomMemberQueryRequest.setUserId(loginUser.getId());
        List<RoomMember> roomMemberList = roomMemberService.list(
                roomMemberService.getQueryWrapper(roomMemberQueryRequest)
        );
        return ResultUtils.success(roomMemberService.getRoomMemberVOList(roomMemberList));
    }
}
