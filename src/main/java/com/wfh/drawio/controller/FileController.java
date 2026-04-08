package com.wfh.drawio.controller;

import cn.hutool.core.io.FileUtil;

import java.util.Arrays;

import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.manager.RustFsManager;
import com.wfh.drawio.model.dto.file.UploadFileRequest;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.enums.FileUploadBizEnum;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Title: FileController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2025/12/23 16:48
 * @description: 文件接口
 */
@Tag(name = "文件管理", description = "文件上传相关接口")
@RestController
@RequestMapping("/file")
@Slf4j
public class FileController {

    @Resource
    private UserService userService;

    @Resource
    private RustFsManager rustFsManager;


    /**
     * 文件上传
     *
     * @param multipartFile     上传的文件
     * @param uploadFileRequest 上传请求参数（包含业务类型biz）
     * @param request           HTTP请求
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    @Operation(summary = "文件上传",
            description = """
                    上传文件到对象存储。

                    **功能说明：**
                    - 根据业务类型（biz）存储文件到对应目录
                    - 目录格式：{biz}/{userId}/{filename}
                    - 返回可访问的文件URL

                    **支持的业务类型：**
                    - USER_AVATAR：用户头像

                    **文件校验：**
                    - 用户头像：最大1MB，支持jpeg/jpg/svg/png/webp

                    **权限要求：**
                    - 需要登录""")
    public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
                                           UploadFileRequest uploadFileRequest, HttpServletRequest request) {
        String biz = uploadFileRequest.getBiz();
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        validFile(multipartFile, fileUploadBizEnum);
        User loginUser = userService.getLoginUser(request);
        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String filepath = String.format("/%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), filename);
        String fileUrl = "";
        try {
            // 上传文件
            fileUrl = rustFsManager.putObject(filepath, multipartFile.getInputStream());
            // 返回可访问地址
            return ResultUtils.success(fileUrl);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

    /**
     * 校验文件
     *
     * @param multipartFile
     * @param fileUploadBizEnum 业务类型
     */
    private void validFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum) {
        // 文件大小
        long fileSize = multipartFile.getSize();
        // 文件后缀
        String fileSuffix = FileUtil.getSuffix(multipartFile.getOriginalFilename());
        final long ONE_M = 1024 * 1024L;
        if (FileUploadBizEnum.USER_AVATAR.equals(fileUploadBizEnum)) {
            if (fileSize > ONE_M) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件大小不能超过 1M");
            }
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
    }
}
