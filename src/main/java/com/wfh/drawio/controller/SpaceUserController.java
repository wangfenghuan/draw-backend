package com.wfh.drawio.controller;

import cn.hutool.core.util.ObjectUtil;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.spaceuser.SpaceUserAddRequest;
import com.wfh.drawio.model.dto.spaceuser.SpaceUserEditRequest;
import com.wfh.drawio.model.dto.spaceuser.SpaceUserQueryRequest;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.SpaceUser;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.vo.SpaceUserVO;
import com.wfh.drawio.service.SpaceRoleService;
import com.wfh.drawio.service.SpaceService;
import com.wfh.drawio.service.SpaceUserService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/spaceUser")
@Slf4j
public class SpaceUserController {

    @Resource
    private SpaceUserService spaceUserService;

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private SpaceRoleService spaceRoleService;

    /**
     * 添加成员到空间
     * @param spaceUserAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @PreAuthorize("hasSpaceAuthority(#spaceUserAddRequest.spaceId, 'space:user:manage') or hasAuthority('admin')")
    @Operation(summary = "添加成员到空间",
            description = """
                    添加成员到团队空间并设置角色。

                    **权限要求：**
                    - 需要登录
                    - 团队空间：需要有空间用户管理权限
                    - 管理员可以添加成员到任何空间

                    **角色说明：**
                    - sapce_admin：空间管理员，拥有所有权限
                    - space_editor：编辑者，可以创建和编辑图表
                    - sapce_viewer：查看者，只能查看图表
                    """)
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest spaceUserAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserAddRequest == null, ErrorCode.PARAMS_ERROR);

        // 注解已经做了权限校验
        long id = spaceUserService.addSpaceUser(spaceUserAddRequest);
        return ResultUtils.success(id);
    }

    /**
     * 从空间移除成员
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @Operation(summary = "从空间移除成员",
            description = """
                    从团队空间中移除成员。

                    **权限要求：**
                    - 需要登录
                    - 团队空间：需要有空间用户管理权限
                    - 管理员可以移除任何成员
                    """)
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody DeleteRequest deleteRequest,
                                                 HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        // 判断是否存在
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);

        // 手动校验空间权限（因为需要先查询 oldSpaceUser 才能知道 spaceId）
        User loginUser = userService.getLoginUser(request);
        if (!spaceRoleService.hasAuthority(loginUser.getId(), oldSpaceUser.getSpaceId(), "space:user:manage")
                && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无空间用户管理权限");
        }

        // 操作数据库
        boolean result = spaceUserService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询某个成员在某个空间的信息
     * @param spaceUserQueryRequest
     * @return
     */
    @PostMapping("/get")
    @Operation(summary = "查询某个成员在某个空间的信息")
    public BaseResponse<SpaceUser> getSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest) {
        // 参数校验
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        Long spaceId = spaceUserQueryRequest.getSpaceId();
        Long userId = spaceUserQueryRequest.getUserId();
        ThrowUtils.throwIf(ObjectUtil.hasEmpty(spaceId, userId), ErrorCode.PARAMS_ERROR);
        // 查询数据库
        SpaceUser spaceUser = spaceUserService.getOne(spaceUserService.getQueryWrapper(spaceUserQueryRequest));
        ThrowUtils.throwIf(spaceUser == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(spaceUser);
    }

    /**
     * 查询成员信息列表
     * @param spaceUserQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list")
    @Operation(summary = "查询成员信息列表")
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest spaceUserQueryRequest,
                                                         HttpServletRequest request) {
        ThrowUtils.throwIf(spaceUserQueryRequest == null, ErrorCode.PARAMS_ERROR);
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }

    /**
     * 编辑成员信息（设置权限）
     * @param spaceUserEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @Operation(summary = "编辑成员信息（设置权限）",
            description = """
                    修改空间成员的角色权限。

                    **权限要求：**
                    - 需要登录
                    - 团队空间：需要有空间用户管理权限
                    - 管理员可以修改任何成员的权限
                    """)
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest spaceUserEditRequest,
                                               HttpServletRequest request) {
        if (spaceUserEditRequest == null || spaceUserEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        SpaceUser spaceUser = new SpaceUser();
        BeanUtils.copyProperties(spaceUserEditRequest, spaceUser);
        // 数据校验
        spaceUserService.validSpaceUser(spaceUser, false);
        // 判断是否存在
        long id = spaceUserEditRequest.getId();
        SpaceUser oldSpaceUser = spaceUserService.getById(id);
        ThrowUtils.throwIf(oldSpaceUser == null, ErrorCode.NOT_FOUND_ERROR);

        // 手动校验空间权限（因为需要先查询 oldSpaceUser 才能知道 spaceId）
        User loginUser = userService.getLoginUser(request);
        if (!spaceRoleService.hasAuthority(loginUser.getId(), oldSpaceUser.getSpaceId(), "space:user:manage")
                && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无空间用户管理权限");
        }

        // 操作数据库
        boolean result = spaceUserService.updateById(spaceUser);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询我加入的团队空间列表
     * @param request
     * @return
     */
    @PostMapping("/list/my")
    @Operation(summary = "查询我加入的团队空间列表")
    public BaseResponse<List<SpaceUserVO>> listMyTeamSpace(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        SpaceUserQueryRequest spaceUserQueryRequest = new SpaceUserQueryRequest();
        spaceUserQueryRequest.setUserId(loginUser.getId());
        List<SpaceUser> spaceUserList = spaceUserService.list(
                spaceUserService.getQueryWrapper(spaceUserQueryRequest)
        );
        return ResultUtils.success(spaceUserService.getSpaceUserVOList(spaceUserList));
    }
}
