package com.wfh.drawio.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.announcement.AnnouncementAddRequest;
import com.wfh.drawio.model.dto.announcement.AnnouncementQueryRequest;
import com.wfh.drawio.model.dto.announcement.AnnouncementUpdateRequest;
import com.wfh.drawio.model.entity.Announcement;
import com.wfh.drawio.model.vo.AnnouncementVO;
import com.wfh.drawio.service.AnnouncementService;
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
 * @Title: AnnouncementController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2026/1/24 17:04
 * @description: 公告管理接口
 */
@Tag(name = "公告管理", description = "系统公告的增删改查接口")
@RestController
@RequestMapping("/announcement")
@Slf4j
public class AnnouncementController {

    @Resource
    private AnnouncementService announcementService;

    @Resource
    private UserService userService;

    /**
     * 创建公告
     *
     * @param announcementAddRequest 公告创建请求
     * @param request                HTTP请求
     * @return 新创建的公告ID
     */
    @PostMapping("/add")
    @Operation(summary = "创建公告",
            description = """
                    创建新的系统公告。

                    **功能说明：**
                    - 创建系统公告记录
                    - 自动关联当前登录用户

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Long> addAnnouncement(@RequestBody AnnouncementAddRequest announcementAddRequest,
                                              HttpServletRequest request) {
        if (announcementAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Announcement announcement = new Announcement();
        BeanUtils.copyProperties(announcementAddRequest, announcement);

        // 从请求中获取当前登录用户ID
        com.wfh.drawio.model.entity.User loginUser = userService.getLoginUser(request);
        announcement.setUserId(loginUser.getId());

        boolean result = announcementService.save(announcement);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(announcement.getId());
    }

    /**
     * 删除公告
     *
     * @param deleteRequest 删除请求（包含公告ID）
     * @param request       HTTP请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @Operation(summary = "删除公告",
            description = """
                    删除指定的系统公告。

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> deleteAnnouncement(@RequestBody DeleteRequest deleteRequest,
                                                    HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = announcementService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新公告
     *
     * @param announcementUpdateRequest 公告更新请求
     * @param request                   HTTP请求
     * @return 是否更新成功
     */
    @PostMapping("/update")
    @Operation(summary = "更新公告",
            description = """
                    更新系统公告内容。

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> updateAnnouncement(@RequestBody AnnouncementUpdateRequest announcementUpdateRequest,
                                                    HttpServletRequest request) {
        if (announcementUpdateRequest == null || announcementUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Announcement announcement = new Announcement();
        BeanUtils.copyProperties(announcementUpdateRequest, announcement);
        boolean result = announcementService.updateById(announcement);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据ID获取公告详情
     *
     * @param id      公告ID
     * @param request HTTP请求
     * @return 公告实体类
     */
    @GetMapping("/get")
    @Operation(summary = "根据ID获取公告",
            description = """
                    根据ID获取公告详细信息。

                    **权限要求：**
                    - 无需登录，所有用户可查询""")
    public BaseResponse<Announcement> getAnnouncementById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Announcement announcement = announcementService.getById(id);
        ThrowUtils.throwIf(announcement == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(announcement);
    }

    /**
     * 根据ID获取公告封装类
     *
     * @param id      公告ID
     * @param request HTTP请求
     * @return 公告封装类（包含用户信息）
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据ID获取公告封装类",
            description = """
                    根据ID获取公告详情（封装类）。

                    **返回内容：**
                    - 公告基本信息
                    - 创建用户信息

                    **权限要求：**
                    - 无需登录，所有用户可查询""")
    public BaseResponse<AnnouncementVO> getAnnouncementVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Announcement announcement = announcementService.getById(id);
        ThrowUtils.throwIf(announcement == null, ErrorCode.NOT_FOUND_ERROR);
        AnnouncementVO announcementVO = announcementService.getAnnouncementVO(announcement);
        return ResultUtils.success(announcementVO);
    }

    /**
     * 分页获取公告列表
     *
     * @param announcementQueryRequest 查询请求（分页参数）
     * @param request                   HTTP请求
     * @return 公告分页列表
     */
    @PostMapping("/list/page")
    @Operation(summary = "分页获取公告列表",
            description = """
                    分页查询公告列表（实体类）。

                    **权限要求：**
                    - 无需登录，所有用户可查询""")
    public BaseResponse<Page<Announcement>> listAnnouncementByPage(@RequestBody AnnouncementQueryRequest announcementQueryRequest,
                                                                    HttpServletRequest request) {
        if (announcementQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = announcementQueryRequest.getCurrent();
        long size = announcementQueryRequest.getPageSize();
        Page<Announcement> announcementPage = announcementService.page(new Page<>(current, size),
                announcementService.getQueryWrapper(announcementQueryRequest));
        return ResultUtils.success(announcementPage);
    }

    /**
     * 分页获取公告封装列表
     *
     * @param announcementQueryRequest 查询请求（分页参数）
     * @param request                   HTTP请求
     * @return 公告封装类分页列表
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页获取公告封装列表",
            description = """
                    分页查询公告列表（封装类）。

                    **返回内容：**
                    - 公告基本信息
                    - 创建用户信息

                    **权限要求：**
                    - 无需登录，所有用户可查询

                    **限制条件：**
                    - 每页最多20条""")
    public BaseResponse<Page<AnnouncementVO>> listAnnouncementVOByPage(@RequestBody AnnouncementQueryRequest announcementQueryRequest,
                                                                        HttpServletRequest request) {
        if (announcementQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = announcementQueryRequest.getCurrent();
        long size = announcementQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Announcement> announcementPage = announcementService.page(new Page<>(current, size),
                announcementService.getQueryWrapper(announcementQueryRequest));
        Page<AnnouncementVO> announcementVOPage = new Page<>(current, size, announcementPage.getTotal());
        List<AnnouncementVO> announcementVOList = announcementService.getAnnouncementVO(announcementPage.getRecords());
        announcementVOPage.setRecords(announcementVOList);
        return ResultUtils.success(announcementVOPage);
    }
}

