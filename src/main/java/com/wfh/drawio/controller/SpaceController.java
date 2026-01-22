package com.wfh.drawio.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.annotation.AuthCheck;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.constant.UserConstant;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.diagram.DiagramQueryRequest;
import com.wfh.drawio.model.dto.space.SpaceAddReqeust;
import com.wfh.drawio.model.dto.space.SpaceEditRequest;
import com.wfh.drawio.model.dto.space.SpaceQueryRequest;
import com.wfh.drawio.model.dto.space.SpaceUpdateRequest;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.enums.SpaceLevelEnum;
import com.wfh.drawio.model.vo.DiagramVO;
import com.wfh.drawio.model.vo.SpaceLevel;
import com.wfh.drawio.model.vo.SpaceVO;
import com.wfh.drawio.service.DiagramService;
import com.wfh.drawio.service.SpaceService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Title: SpaceController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2026/1/6 09:50
 * @description:
 */
@RestController
@RequestMapping("/space")
public class SpaceController {

    @Resource
    private SpaceService spaceService;

    @Resource
    private UserService userService;

    @Resource
    private DiagramService diagramService;


    /**
     * 创建空间
     * @param spaceAddReqeust
     * @param request
     * @return
     */
    @PostMapping("/add")
    @Operation(summary = "创建空间")
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddReqeust spaceAddReqeust, HttpServletRequest request){
        User loginUser = userService.getLoginUser(request);
        long l = spaceService.addSpace(spaceAddReqeust, loginUser);
        return ResultUtils.success(l);
    }

    /**
     * 更新空间信息
     * 管理员专用的空间信息更新接口
     *
     * @param spaceUpdateRequest 空间更新请求
     * @return 是否更新成功
     */
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "更新空间信息（管理员专用）",
            description = """
                    管理员专用的空间信息更新接口。

                    **权限要求：**
                    - 仅限admin角色使用

                    **注意事项：**
                    - 如果修改了空间级别，会自动重新设置maxCount和maxSize
                    - 不会影响当前的totalSize和totalCount
                    """)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest spaceUpdateRequest) {
        if (spaceUpdateRequest == null || spaceUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 将实体类和 DTO 进行转换
        Space space = new Space();
        BeanUtils.copyProperties(spaceUpdateRequest, space);
        // 自动填充数据
        spaceService.fillSpaceBySpaceLevel(space);
        // 数据校验
        spaceService.validSpace(space, false);
        // 判断是否存在
        long id = spaceUpdateRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 删除空间
     * 删除空间及其内部的所有图表
     *
     * @param deleteRequest 删除请求（包含空间ID）
     * @param request HTTP请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @Operation(summary = "删除空间",
            description = """
                    删除指定的空间，并自动删除空间内的所有图表。

                    **功能说明：**
                    - 删除空间记录
                    - 级联删除空间内的所有图表
                    - 使用事务确保删除操作的原子性

                    **额度处理：**
                    - 删除空间不会释放额度（因为空间本身被删除了）
                    - 删除图表时也不会释放额度（因为关联的空间也被删除了）

                    **权限要求：**
                    - 需要登录
                    - 仅空间创建人或管理员可删除

                    **注意事项：**
                    - 删除操作不可逆，请谨慎操作
                    - 删除后空间内的所有图表都会被删除
                    - 对象存储中的文件不会自动删除（可通过定时任务清理）
                    """)
    public BaseResponse<Boolean> deleteSpace(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long id = deleteRequest.getId();
        User loginUser = userService.getLoginUser(request);

        // 判断是否存在
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);

        // 仅空间创建人或管理员可删除
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 使用service层方法处理业务逻辑（包含关联删除图表）
        spaceService.deleteSpaceWithDiagrams(id);

        return ResultUtils.success(true);
    }

    /**
     * 查询空间级别列表
     * 获取所有可用的空间级别信息
     *
     * @return 空间级别列表
     */
    @GetMapping("/list/level")
    @Operation(summary = "查询空间级别列表",
            description = """
                    获取所有可用的空间级别信息，用于前端展示空间等级和对应的额度限制。

                    **返回内容：**
                    - value：级别值（0=普通版，1=专业版，2=旗舰版）
                    - text：级别名称（"普通版"、"专业版"、"旗舰版"）
                    - maxCount：最大图表数量
                    - maxSize：最大存储空间（字节）

                    **级别说明：**
                    - **普通版（value=0）：**
                      - 最大100个图表
                      - 最大100MB存储空间
                    - **专业版（value=1）：**
                      - 最大1000个图表
                      - 最大1000MB存储空间
                    - **旗舰版（value=2）：**
                      - 最大10000个图表
                      - 最大10000MB存储空间

                    **权限要求：**
                    - 无需登录，所有用户可查询
                    """)
    public BaseResponse<List<SpaceLevel>> listSpaceLevel() {
        List<SpaceLevel> spaceLevelList = Arrays.stream(SpaceLevelEnum.values())
                .map(spaceLevelEnum -> new SpaceLevel(
                        spaceLevelEnum.getValue(),
                        spaceLevelEnum.getText(),
                        spaceLevelEnum.getMaxCount(),
                        spaceLevelEnum.getMaxSize()))
                .collect(Collectors.toList());
        return ResultUtils.success(spaceLevelList);
    }

    // region 增删改查

    /**
     * 根据ID获取空间详情（封装类）
     * 获取指定空间的详细信息
     *
     * @param id 空间ID
     * @param request HTTP请求
     * @return 空间详情（封装类）
     */
    @GetMapping("/get/vo")
    @PreAuthorize("@spaceSecurityService.hasSpaceAuthority(#id, 'space:diagram:view') or hasAuthority('admin')")
    @Operation(summary = "获取空间详情",
            description = """
                    根据ID获取空间的详细信息。

                    **权限要求：**
                    - 需要登录
                    - 仅空间创建人或管理员可查看

                    **返回内容：**
                    - 空间基本信息（ID、名称、级别等）
                    - 空间额度信息（maxCount、maxSize、totalCount、totalSize）
                    """)
    public BaseResponse<SpaceVO> getSpaceVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVO(space, request));
    }

    /**
     * 根据ID获取空间（仅管理员）
     * 管理员专用的空间查询接口
     *
     * @param id 空间ID
     * @param request HTTP请求
     * @return 空间实体类
     */
    @GetMapping("/get")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "获取空间（管理员专用）",
            description = """
                    管理员专用的空间查询接口，获取空间实体类。

                    **权限要求：**
                    - 仅限admin角色使用
                    """)
    public BaseResponse<Space> getSpaceById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Space space = spaceService.getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(space);
    }

    /**
     * 分页获取空间列表（仅管理员）
     * 管理员专用的空间列表查询接口
     *
     * @param spaceQueryRequest 查询请求
     * @return 空间列表（分页）
     */
    @PostMapping("/list/page")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "分页查询空间（管理员专用）",
            description = """
                    管理员专用的空间列表查询接口，可以查询所有空间。

                    **权限要求：**
                    - 仅限admin角色使用

                    **查询条件：**
                    - 支持按空间名称、ID、用户ID、空间级别等条件查询
                    - 支持分页查询
                    """)
    public BaseResponse<Page<Space>> listSpaceByPage(@RequestBody SpaceQueryRequest spaceQueryRequest) {
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        return ResultUtils.success(spacePage);
    }

    /**
     * 分页获取空间列表（封装类）
     * 查询空间列表，支持多种查询条件
     *
     * @param spaceQueryRequest 查询请求
     * @param request HTTP请求
     * @return 空间列表（封装类，分页）
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页查询空间列表",
            description = """
                    查询空间列表，支持按条件筛选。

                    **权限要求：**
                    - 需要登录
                    - 只能查询自己创建的空间

                    **限制条件：**
                    - 每页最多20条（防止爬虫）
                    - 支持按名称、级别等条件筛选
                    """)
    public BaseResponse<Page<SpaceVO>> listSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                          HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        spaceQueryRequest.setUserId(loginUser.getId());
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    /**
     * 分页获取当前登录用户创建的空间列表
     * 查询自己创建的所有空间
     *
     * @param spaceQueryRequest 查询请求
     * @param request HTTP请求
     * @return 我的空间列表（封装类，分页）
     */
    @PostMapping("/my/list/page/vo")
    @Operation(summary = "查询我的空间",
            description = """
                    查询当前登录用户创建的所有空间。

                    **权限要求：**
                    - 需要登录
                    - 只能查询自己创建的空间

                    **限制条件：**
                    - 每页最多20条（防止爬虫）
                    - 支持按名称等条件筛选
                    """)
    public BaseResponse<Page<SpaceVO>> listMySpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                            HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        spaceQueryRequest.setUserId(loginUser.getId());
        long current = spaceQueryRequest.getCurrent();
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Space> spacePage = spaceService.page(new Page<>(current, size),
                spaceService.getQueryWrapper(spaceQueryRequest));
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    /**
     * 分页获取当前登录用户加入的空间列表
     * 查询用户加入的所有团队空间
     *
     * @param spaceQueryRequest 查询请求
     * @param request HTTP请求
     * @return 加入的空间列表（封装类，分页）
     */
    @PostMapping("/joined/list/page/vo")
    @Operation(summary = "查询我加入的空间",
            description = """
                    查询当前登录用户加入的所有团队空间。

                    **权限要求：**
                    - 需要登录
                    - 只能查询自己作为成员加入的团队空间

                    **功能说明：**
                    - 查询用户在 space_user 表中有关联记录的团队空间
                    - 不包括用户自己创建的私有空间

                    **限制条件：**
                    - 每页最多20条（防止爬虫）
                    - 支持按名称、级别等条件筛选
                    """)
    public BaseResponse<Page<SpaceVO>> listJoinedSpaceVOByPage(@RequestBody SpaceQueryRequest spaceQueryRequest,
                                                                HttpServletRequest request) {
        ThrowUtils.throwIf(spaceQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        long size = spaceQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Space> spacePage = spaceService.listJoinedSpaces(spaceQueryRequest, loginUser.getId());
        // 获取封装类
        return ResultUtils.success(spaceService.getSpaceVOPage(spacePage, request));
    }

    /**
     * 编辑空间信息（给用户使用）
     * 用户编辑自己的空间信息
     *
     * @param spaceEditRequest 空间编辑请求
     * @param request HTTP请求
     * @return 是否编辑成功
     */
    @PostMapping("/edit")
    @Operation(summary = "编辑空间信息",
            description = """
                    用户编辑自己的空间信息，目前支持修改空间名称。

                    **权限要求：**
                    - 需要登录
                    - 仅空间创建人可编辑

                    **可编辑字段：**
                    - spaceName：空间名称

                    **不可编辑字段：**
                    - spaceLevel：空间级别（如需升级，请联系管理员）
                    - maxCount、maxSize：由空间级别自动决定
                    """)
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest spaceEditRequest,
                                           HttpServletRequest request) {
        if (spaceEditRequest == null || spaceEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Space space = new Space();
        BeanUtils.copyProperties(spaceEditRequest, space);
        // 数据校验
        spaceService.validSpace(space, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = spaceEditRequest.getId();
        Space oldSpace = spaceService.getById(id);
        ThrowUtils.throwIf(oldSpace == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldSpace.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = spaceService.updateById(space);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据空间ID查询图表列表
     * 查询指定空间下的所有图表
     *
     * @param diagramQueryRequest 查询请求（分页参数）
     * @param request HTTP请求
     * @return 图表列表（封装类，分页）
     */
    @PostMapping("/list/diagrams")
    @PreAuthorize("@spaceSecurityService.hasSpaceAuthority(#diagramQueryRequest.spaceId, 'space:diagram:view') or hasAuthority('admin')")
    @Operation(summary = "查询空间下的图表列表",
            description = """
                    查询指定空间下的所有图表。

                    **权限要求：**
                    - 需要登录
                    - 团队空间：需要是空间成员且有查看权限
                    - 私有空间：仅空间创建人可查询

                    **功能说明：**
                    - 返回指定空间下的所有图表
                    - 支持分页查询
                    - 支持按图表名称等条件筛选

                    **限制条件：**
                    - 每页最多20条（防止爬虫）
                    """)
    public BaseResponse<Page<DiagramVO>> listDiagramsBySpaceId(
            @RequestBody DiagramQueryRequest diagramQueryRequest,
            HttpServletRequest request) {
        Long spaceId = diagramQueryRequest.getSpaceId();
        ThrowUtils.throwIf(spaceId <= 0, ErrorCode.PARAMS_ERROR, "空间ID不能为空");

        // 注解已经做了权限校验
        // 查询空间是否存在
        Space space = spaceService.getById(spaceId);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");

        // 构建查询请求
        if (diagramQueryRequest == null) {
            diagramQueryRequest = new DiagramQueryRequest();
        }
        diagramQueryRequest.setSpaceId(spaceId);

        long current = diagramQueryRequest.getCurrent();
        long size = diagramQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);

        // 查询数据库
        Page<Diagram> diagramPage = diagramService.page(
                new Page<>(current, size),
                diagramService.getQueryWrapper(diagramQueryRequest));

        // 获取封装类
        Page<DiagramVO> diagramVOPage = diagramService.getDiagramVOPage(diagramPage, request);

        return ResultUtils.success(diagramVOPage);
    }

    // endregion

}
