package com.wfh.drawio.controller;

import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.annotation.AuthCheck;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.constant.UserConstant;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.manager.MinioManager;
import com.wfh.drawio.model.dto.diagram.*;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.entity.RoomSnapshots;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.enums.FileUploadBizEnum;
import com.wfh.drawio.model.vo.DiagramVO;
import com.wfh.drawio.service.*;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;


/**
 * 图表接口
 *
 * @author fenghuanwang
 */
@RestController
@RequestMapping("/diagram")
@Slf4j
public class DiagramController {

    @Resource
    private DiagramService diagramService;

    @Resource
    private UserService userService;

    @Resource
    private MinioManager minioManager;

    @Resource
    private RoomSnapshotsService snapshotsService;

    @Resource
    private RoomUpdatesService updatesService;

    /**
     * 检查是否有上传权限，枪锁
     * @param roomId
     * @return
     */
    @GetMapping("/check-lock/{roomId}")
    public boolean checkLock(@PathVariable Long roomId) {
        return diagramService.tryAcquireLock(String.valueOf(roomId));
    }

    /**
     * 上传图表快照
     * @param roomId
     * @param snampshotData
     * @return
     */
    @PostMapping("/uploadSnapshot/{roomId}")
    public BaseResponse<Boolean> uploadSnapshot(@PathVariable Long roomId, @RequestBody byte[] snampshotData){
        RoomSnapshots byId = snapshotsService.getById(roomId);
        if (byId == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "房间不存在");
        }
        byId.setLastUpdateId(0L);
        byId.setSnapshotData(snampshotData);
        // 更新数据库
        boolean update = snapshotsService.updateById(byId);
        // 异步触发清理任务
        updatesService.cleanOldUpdates(roomId);
        return ResultUtils.success(update);
    }


    /**
     * 上传图表到minio
     * @param multipartFile
     * @param diagramUploadRequest
     * @param request
     * @return
     */
    @PostMapping("/upload")
    private BaseResponse<String> uploadDiagram(@RequestPart("file") MultipartFile multipartFile, @RequestBody DiagramUploadRequest diagramUploadRequest, HttpServletRequest request){
        String biz = diagramUploadRequest.getBiz();
        Long diagramId = diagramUploadRequest.getDiagramId();
        Long userId = diagramUploadRequest.getUserId();
        if (diagramId == null || userId == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
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
        String extension = FilenameUtils.getExtension(filename);
        try {
            // 上传文件
            fileUrl = minioManager.putObject(filepath, multipartFile.getInputStream(), multipartFile);
            // 根据文件不同类型插入到不同字段中
            // 更新到数据库
            Diagram diagram = new Diagram();
            diagram.setId(diagramId);
            if (extension.equals("SVG")){
                diagram.setSvgUrl(fileUrl);
            } else if (extension.equals("PNG")) {
                diagram.setPictureUrl(fileUrl);
            }
            diagramService.updateById(diagram);
            // 返回可访问地址
            return ResultUtils.success(fileUrl);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

    /**
     * 流式代理下载接口
     * * @param remoteUrl 远程文件地址
     * @param fileName
     * @param response  HttpServletResponse 对象，用于直接操作输出流
     */
    @GetMapping("/stream-download")
    public void downloadRemoteFile(@RequestParam(required = false) String fileName,
                                   @RequestParam() String type,
                                   @RequestParam() Long diagramId,
                                   HttpServletResponse response, HttpServletRequest request) {

        User loginUser = userService.getLoginUser(request);
        Diagram diagram = diagramService.getById(diagramId);
        if (diagram == null){
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表不存在");
        }
        if (!diagram.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        Long id = diagram.getId();
        StrategyContext strategyContext = new StrategyContext();
        switch (type){
            case "SVG":
                strategyContext.setDownloadStrategy(new SvgDownloadStrategy());
                strategyContext.execDownload(id, fileName, response);
                break;
            case "PNG":
                strategyContext.setDownloadStrategy(new PngDownloadStrategy());
                strategyContext.execDownload(id, fileName, response);
                break;
            case "XML":
                strategyContext.setDownloadStrategy(new XmlDownloadStrategy());
                strategyContext.execDownload(id, fileName, response);
                break;
            default:
                break;
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
            if (!Arrays.asList("jpeg", "jpg", "svg", "png", "webp", "svg").contains(fileSuffix)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "文件类型错误");
            }
        }
    }

    // region 增删改查

    /**
     * 创建图表
     *
     * @param diagramAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    public BaseResponse<Long> addDiagram(@RequestBody DiagramAddRequest diagramAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(diagramAddRequest == null, ErrorCode.PARAMS_ERROR);
        Diagram diagram = new Diagram();
        BeanUtils.copyProperties(diagramAddRequest, diagram);
        User loginUser = userService.getLoginUser(request);
        diagram.setUserId(loginUser.getId());
        // 数据校验
        diagramService.validDiagram(diagram, true);
        // 写入数据库
        boolean result = diagramService.save(diagram);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        // 返回新写入的数据 id
        long newDiagramId = diagram.getId();
        return ResultUtils.success(newDiagramId);
    }

    /**
     * 删除图表
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteDiagram(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        long id = deleteRequest.getId();
        // 判断是否存在
        Diagram oldDiagram = diagramService.getById(id);
        ThrowUtils.throwIf(oldDiagram == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可删除
        if (!oldDiagram.getUserId().equals(user.getId()) && !userService.isAdmin(request)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = diagramService.removeById(id);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新图表（仅管理员可用）
     *
     * @param diagramUpdateRequest
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateDiagram(@RequestBody DiagramUpdateRequest diagramUpdateRequest) {
        if (diagramUpdateRequest == null || diagramUpdateRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Diagram diagram = new Diagram();
        BeanUtils.copyProperties(diagramUpdateRequest, diagram);
        // 数据校验
        diagramService.validDiagram(diagram, false);
        // 判断是否存在
        long id = diagramUpdateRequest.getId();
        Diagram oldDiagram = diagramService.getById(id);
        ThrowUtils.throwIf(oldDiagram == null, ErrorCode.NOT_FOUND_ERROR);
        // 操作数据库
        boolean result = diagramService.updateById(diagram);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取图表（封装类）
     *
     * @param id
     * @return
     */
    @GetMapping("/get/vo")
    public BaseResponse<DiagramVO> getDiagramVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Diagram diagram = diagramService.getById(id);
        ThrowUtils.throwIf(diagram == null, ErrorCode.NOT_FOUND_ERROR);
        // 获取封装类
        return ResultUtils.success(diagramService.getDiagramVO(diagram, request));
    }

    /**
     * 分页获取图表列表（仅管理员可用）
     *
     * @param diagramQueryRequest
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<Diagram>> listDiagramByPage(@RequestBody DiagramQueryRequest diagramQueryRequest) {
        long current = diagramQueryRequest.getCurrent();
        long size = diagramQueryRequest.getPageSize();
        // 查询数据库
        Page<Diagram> diagramPage = diagramService.page(new Page<>(current, size),
                diagramService.getQueryWrapper(diagramQueryRequest));
        return ResultUtils.success(diagramPage);
    }

    /**
     * 分页获取图表列表（封装类）
     *
     * @param diagramQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    public BaseResponse<Page<DiagramVO>> listDiagramVOByPage(@RequestBody DiagramQueryRequest diagramQueryRequest,
                                                               HttpServletRequest request) {
        long current = diagramQueryRequest.getCurrent();
        long size = diagramQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Diagram> diagramPage = diagramService.page(new Page<>(current, size),
                diagramService.getQueryWrapper(diagramQueryRequest));
        // 获取封装类
        return ResultUtils.success(diagramService.getDiagramVOPage(diagramPage, request));
    }

    /**
     * 分页获取当前登录用户创建的图表列表
     *
     * @param diagramQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/my/list/page/vo")
    public BaseResponse<Page<DiagramVO>> listMyDiagramVOByPage(@RequestBody DiagramQueryRequest diagramQueryRequest,
                                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(diagramQueryRequest == null, ErrorCode.PARAMS_ERROR);
        // 补充查询条件，只查询当前登录用户的数据
        User loginUser = userService.getLoginUser(request);
        diagramQueryRequest.setUserId(loginUser.getId());
        long current = diagramQueryRequest.getCurrent();
        long size = diagramQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Page<Diagram> diagramPage = diagramService.page(new Page<>(current, size),
                diagramService.getQueryWrapper(diagramQueryRequest));
        // 获取封装类
        return ResultUtils.success(diagramService.getDiagramVOPage(diagramPage, request));
    }

    /**
     * 编辑图表（给用户使用）
     *
     * @param diagramEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    public BaseResponse<Boolean> editDiagram(@RequestBody DiagramEditRequest diagramEditRequest, HttpServletRequest request) {
        if (diagramEditRequest == null || diagramEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Diagram diagram = new Diagram();
        BeanUtils.copyProperties(diagramEditRequest, diagram);
        // 数据校验
        diagramService.validDiagram(diagram, false);
        User loginUser = userService.getLoginUser(request);
        // 判断是否存在
        long id = diagramEditRequest.getId();
        Diagram oldDiagram = diagramService.getById(id);
        ThrowUtils.throwIf(oldDiagram == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldDiagram.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 操作数据库
        boolean result = diagramService.updateById(diagram);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    // endregion
}
