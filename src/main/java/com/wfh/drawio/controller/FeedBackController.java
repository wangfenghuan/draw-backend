package com.wfh.drawio.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.manager.RustFsManager;
import com.wfh.drawio.model.dto.feedback.FeedbackAddRequest;
import com.wfh.drawio.model.dto.feedback.FeedbackQueryRequest;
import com.wfh.drawio.model.entity.Feedback;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.vo.FeedbackVO;
import com.wfh.drawio.service.FeedbackService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * @Title: FeedBackController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2026/1/24 20:18
 * @description: 用户反馈接口
 */
@RestController
@RequestMapping("/feedback")
@Slf4j
public class FeedBackController {

    @Resource
    private FeedbackService feedbackService;

    @Resource
    private UserService userService;

    @Resource
    private RustFsManager rustFsManager;

    /**
     * 上传反馈图片
     *
     * @param file
     * @param request
     * @return
     */
    @PostMapping("/upload/image")
    @Operation(summary = "上传反馈图片")
    public BaseResponse<String> uploadFeedbackImage(@RequestPart("file") MultipartFile file,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");

        // 校验文件大小（最大5MB）
        long maxSize = 5 * 1024 * 1024L;
        ThrowUtils.throwIf(file.getSize() > maxSize, ErrorCode.PARAMS_ERROR, "文件大小不能超过5MB");

        // 校验文件类型
        String contentType = file.getContentType();
        ThrowUtils.throwIf(contentType == null || !contentType.startsWith("image/"),
                ErrorCode.PARAMS_ERROR, "只能上传图片文件");

        User loginUser = userService.getLoginUser(request);
        // 文件目录：feedback/{userId}/{filename}
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + file.getOriginalFilename();
        String filepath = String.format("feedback/%s/%s", loginUser.getId(), filename);

        try {
            // 上传文件
            String fileUrl = rustFsManager.putObject(filepath, file.getInputStream());
            // 返回可访问地址
            return ResultUtils.success(fileUrl);
        } catch (Exception e) {
            log.error("feedback image upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

    /**
     * 添加反馈
     *
     * @param feedbackAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @Operation(summary = "添加反馈")
    public BaseResponse<Long> addFeedback(@RequestBody FeedbackAddRequest feedbackAddRequest,
                                          HttpServletRequest request) {
        if (feedbackAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 校验反馈内容
        ThrowUtils.throwIf(feedbackAddRequest.getContent() == null ||
                        feedbackAddRequest.getContent().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "反馈内容不能为空");

        // 校验反馈内容长度（最多2000字符）
        ThrowUtils.throwIf(feedbackAddRequest.getContent().length() > 2000,
                ErrorCode.PARAMS_ERROR, "反馈内容不能超过2000字符");

        Feedback feedback = new Feedback();
        BeanUtils.copyProperties(feedbackAddRequest, feedback);

        // 从请求中获取当前登录用户ID
        User loginUser = userService.getLoginUser(request);
        feedback.setUserId(loginUser.getId());

        boolean result = feedbackService.save(feedback);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(feedback.getId());
    }

    /**
     * 根据 id 获取反馈
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @Operation(summary = "根据 id 获取反馈")
    public BaseResponse<Feedback> getFeedbackById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Feedback feedback = feedbackService.getById(id);
        ThrowUtils.throwIf(feedback == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(feedback);
    }

    /**
     * 根据 id 获取反馈封装类
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据 id 获取反馈封装类")
    public BaseResponse<FeedbackVO> getFeedbackVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Feedback feedback = feedbackService.getById(id);
        ThrowUtils.throwIf(feedback == null, ErrorCode.NOT_FOUND_ERROR);
        FeedbackVO feedbackVO = feedbackService.getFeedbackVO(feedback);
        return ResultUtils.success(feedbackVO);
    }

    /**
     * 分页获取反馈列表
     *
     * @param feedbackQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    @Operation(summary = "分页获取反馈列表")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Page<Feedback>> listFeedbackByPage(@RequestBody FeedbackQueryRequest feedbackQueryRequest,
                                                            HttpServletRequest request) {
        if (feedbackQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = feedbackQueryRequest.getCurrent();
        long size = feedbackQueryRequest.getPageSize();
        Page<Feedback> feedbackPage = feedbackService.page(new Page<>(current, size),
                feedbackService.getQueryWrapper(feedbackQueryRequest));
        return ResultUtils.success(feedbackPage);
    }

    /**
     * 分页获取反馈封装列表
     *
     * @param feedbackQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页获取反馈封装列表")
    public BaseResponse<Page<FeedbackVO>> listFeedbackVOByPage(@RequestBody FeedbackQueryRequest feedbackQueryRequest,
                                                                HttpServletRequest request) {
        if (feedbackQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = feedbackQueryRequest.getCurrent();
        long size = feedbackQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Feedback> feedbackPage = feedbackService.page(new Page<>(current, size),
                feedbackService.getQueryWrapper(feedbackQueryRequest));
        Page<FeedbackVO> feedbackVOPage = new Page<>(current, size, feedbackPage.getTotal());
        List<FeedbackVO> feedbackVOList = feedbackService.getFeedbackVO(feedbackPage.getRecords());
        feedbackVOPage.setRecords(feedbackVOList);
        return ResultUtils.success(feedbackVOPage);
    }

    /**
     * 获取我提交的反馈列表
     *
     * @param feedbackQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    @Operation(summary = "获取我提交的反馈列表")
    public BaseResponse<Page<FeedbackVO>> listMyFeedbackVOByPage(@RequestBody FeedbackQueryRequest feedbackQueryRequest,
                                                                  HttpServletRequest request) {
        if (feedbackQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }

        // 获取当前登录用户ID
        User loginUser = userService.getLoginUser(request);

        // 设置查询条件为当前用户
        feedbackQueryRequest.setUserId(loginUser.getId());

        long current = feedbackQueryRequest.getCurrent();
        long size = feedbackQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Feedback> feedbackPage = feedbackService.page(new Page<>(current, size),
                feedbackService.getQueryWrapper(feedbackQueryRequest));
        Page<FeedbackVO> feedbackVOPage = new Page<>(current, size, feedbackPage.getTotal());
        List<FeedbackVO> feedbackVOList = feedbackService.getFeedbackVO(feedbackPage.getRecords());
        feedbackVOPage.setRecords(feedbackVOList);
        return ResultUtils.success(feedbackVOPage);
    }
}

