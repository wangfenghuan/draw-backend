package com.wfh.drawio.controller;

import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.common.*;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.manager.RustFsManager;
import com.wfh.drawio.model.dto.diagram.*;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.entity.RoomSnapshots;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.enums.FileUploadBizEnum;
import com.wfh.drawio.model.vo.DiagramVO;
import com.wfh.drawio.service.*;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


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
    private RustFsManager rustFsManager;

    @Resource
    private RoomSnapshotsService snapshotsService;

    @Resource
    private RoomUpdatesService updatesService;

    @Resource
    private SpaceService spaceService;

    /**
     * 检查是否有上传权限，抢锁
     * 用于协作场景，抢到锁的客户端负责上传图表操作快照
     *
     * @param roomId 房间ID
     * @return 是否抢锁成功
     */
    @GetMapping("/check-lock/{roomId}")
    @Operation(summary = "检查上传权限并抢锁",
            description = "用于协作场景，多个客户端同时编辑时，抢到锁的客户端负责上传图表操作快照到服务器。" +
                    "抢锁成功后有5分钟的冷却期，冷却期内其他客户端无法抢锁。")
    public boolean checkLock(@PathVariable Long roomId) {
        return diagramService.tryAcquireLock(String.valueOf(roomId));
    }

    /**
     * 上传图表快照
     * 保存协作房间的图表状态快照
     *
     * @param roomId 房间ID
     * @param snampshotData 快照数据（字节数组）
     * @return 是否上传成功
     */
    @PostMapping("/uploadSnapshot/{roomId}")
    @Operation(summary = "上传图表快照",
            description = "保存协作房间的图表状态快照。上传成功后会异步清理旧的操作记录，" +
                    "只保留最近的状态，减少存储空间占用。")
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
     * 上传图表文件到MinIO对象存储
     * 支持SVG和PNG格式，自动更新空间额度
     *
     * @param multipartFile 图表文件（支持SVG、PNG格式）
     * @param diagramUploadRequest 上传请求参数
     * @param request HTTP请求
     * @return 文件访问URL
     */
    @PostMapping("/upload")
    @Operation(summary = "上传图表文件",
            description = """
                    上传图表文件（SVG或PNG格式）到对象存储，并更新图表记录和空间额度。

                    **功能说明：**
                    - 支持SVG和PNG两种格式的图表文件上传
                    - 如果指定了spaceId，文件会存储到私有空间，并计入空间额度
                    - 如果未指定spaceId，文件会存储到公共图库，不计入额度

                    **空间额度影响：**
                    - **私有空间（spaceId不为空）：**
                      - 文件大小会计入空间的totalSize（总大小）
                      - 图表数量会计入空间的totalCount（图表数量）
                      - 只有空间创建人（管理员）才能上传
                      - 上传前会校验空间额度是否充足
                      - picSize = svgSize + pngSize（同时支持两种格式）
                    - **公共图库（spaceId为空）：**
                      - 不计入任何空间额度
                      - 任何登录用户都可以上传

                    **额度计算规则：**
                    - 首次上传SVG：picSize增加svgSize
                    - 首次上传PNG：picSize增加pngSize
                    - 替换SVG：picSize = (picSize - 旧svgSize) + 新svgSize
                    - 替换PNG：picSize = (picSize - 旧pngSize) + 新pngSize

                    **权限要求：**
                    - 需要登录
                    - 私有空间：仅空间创建人可上传
                    - 公共图库：所有登录用户可上传
                    """)
    public BaseResponse<String> uploadDiagram(@RequestPart("file") MultipartFile multipartFile, @RequestPart("diagramUploadRequest") DiagramUploadRequest diagramUploadRequest, HttpServletRequest request){
        String biz = diagramUploadRequest.getBiz();
        Long diagramId = diagramUploadRequest.getDiagramId();
        Long userId = diagramUploadRequest.getUserId();
        Long spaceId = diagramUploadRequest.getSpaceId();
        User loginUser = userService.getLoginUser(request);
        if (diagramId == null || userId == null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
        if (fileUploadBizEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        diagramService.validDiagramFile(multipartFile, fileUploadBizEnum);

        // 文件目录：根据业务、用户来划分
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + multipartFile.getOriginalFilename();
        String filepath;

        // 获取文件大小
        long fileSize = multipartFile.getSize();

        // 构建文件路径
        if (spaceId != null){
            filepath = String.format("space/%s/%s/%s/%s/%s", spaceId, fileUploadBizEnum.getValue(), loginUser.getId(), diagramId, filename);
        }else {
            filepath = String.format("public/%s/%s/%s/%s", fileUploadBizEnum.getValue(), loginUser.getId(), diagramId, filename);
        }

        String fileUrl = "";
        String extension = FilenameUtils.getExtension(filename);
        try {
            // 上传文件
            fileUrl = rustFsManager.putObject(filepath, multipartFile.getInputStream());

            // 使用service层方法处理业务逻辑（包含额度校验和更新）
            diagramService.uploadDiagramWithQuota(diagramId, spaceId, fileUrl, fileSize, extension, loginUser);

            // 返回可访问地址
            return ResultUtils.success(fileUrl);
        } catch (Exception e) {
            log.error("file upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

    /**
     * 流式代理下载接口
     * 根据图表ID和文件类型，流式下载图表文件
     *
     * @param fileName 下载后的文件名（可选，不传则使用默认文件名）
     * @param type 文件类型，支持：SVG、PNG、XML
     * @param diagramId 图表ID
     * @param response HTTP响应对象
     * @param request HTTP请求对象
     */
    @GetMapping("/stream-download")
    @Operation(summary = "下载图表文件",
            description = """
                    根据图表ID和文件类型，从对象存储流式下载图表文件。

                    **支持格式：**
                    - **SVG：** 矢量图格式，可缩放不失真
                    - **PNG：** 位图格式，适合展示
                    - **XML：** DrawIO原生格式，包含完整的图表结构

                    **权限要求：**
                    - 需要登录
                    - 仅图表创建人或管理员可下载

                    **下载方式：**
                    - 采用流式代理下载，直接从对象存储读取并写入响应流
                    - 不占用服务器内存，适合大文件下载
                    - 自动设置正确的Content-Type和Content-Disposition响应头
                    """)
    public void downloadRemoteFile(@RequestParam(required = false) String fileName,
                                   @RequestParam(required = true) String type,
                                   @RequestParam(required = true) Long diagramId,
                                   HttpServletResponse response, HttpServletRequest request) {
            User loginUser = userService.getLoginUser(request);
            Diagram diagram = diagramService.getById(diagramId);
            if (diagram == null){
                throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图表不存在");
            }
            if (!diagram.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
            diagramService.downloadDiagramFile(diagramId, type, fileName, response);
    }

    // region 增删改查

    /**
     * 创建图表
     * 创建一个新的图表记录，可以关联到私有空间或公共图库
     *
     * @param diagramAddRequest 图表创建请求
     * @param request HTTP请求
     * @return 新创建的图表ID
     */
    @PostMapping("/add")
    @Operation(summary = "创建图表",
            description = """
                    创建一个新的图表记录。

                    **空间额度影响：**
                    - **私有空间（spaceId不为空）：**
                      - 图表数量会计入空间的totalCount
                      - 只有空间创建人才能创建
                      - 创建前会校验空间图表数量是否充足
                      - picSize初始为0，后续上传文件时会更新
                    - **公共图库（spaceId为空）：**
                      - 不计入任何空间额度
                      - 所有登录用户都可以创建

                    **权限要求：**
                    - 需要登录
                    - 私有空间：仅空间创建人可创建
                    - 公共图库：所有登录用户可创建

                    **业务流程：**
                    1. 校验空间是否存在（如果指定了spaceId）
                    2. 校验用户权限
                    3. 校验空间额度（图表数量）
                    4. 创建图表记录
                    5. 更新空间totalCount
                    """)
    public BaseResponse<Long> addDiagram(@RequestBody DiagramAddRequest diagramAddRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(diagramAddRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);

        // 使用service层方法处理业务逻辑（包含额度管理）
        Long newDiagramId = diagramService.addDiagramWithQuota(diagramAddRequest, loginUser);

        return ResultUtils.success(newDiagramId);
    }

    /**
     * 删除图表
     * 删除指定的图表，并自动释放空间额度
     *
     * @param deleteRequest 删除请求（包含图表ID）
     * @param request HTTP请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @Operation(summary = "删除图表",
            description = """
                    删除指定的图表，并自动释放空间额度。

                    **空间额度影响：**
                    - **私有空间：**
                      - 释放空间的totalSize（减去图表的picSize）
                      - 减少空间的totalCount（图表数量减1）
                      - picSize = svgSize + pngSize
                    - **公共图库：**
                      - 不影响任何空间额度

                    **权限要求：**
                    - 需要登录
                    - 仅图表创建人或管理员可删除
                    - 私有空间的图表需要额外的空间权限校验

                    **注意事项：**
                    - 删除操作使用事务，确保额度释放和图表删除原子性
                    - 删除后无法恢复，请谨慎操作
                    - 对象存储中的文件不会自动删除（可通过定时任务清理）
                    """)
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
        spaceService.checkDiagramAuth(user, oldDiagram);

        // 使用service层方法处理业务逻辑（包含释放额度）
        diagramService.deleteDiagramWithQuota(id);

        return ResultUtils.success(true);
    }

    /**
     * 更新图表（仅管理员可用）
     * 管理员专用的图表更新接口
     *
     * @param diagramUpdateRequest 图表更新请求
     * @return 是否更新成功
     */
    @PostMapping("/update")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "更新图表（管理员专用）",
            description = """
                    管理员专用的图表更新接口，可以更新任意图表信息。

                    **权限要求：**
                    - 仅限admin角色使用

                    **注意事项：**
                    - 此接口不会影响空间额度
                    - 如果需要修改文件大小相关的信息，请谨慎操作
                    """)
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
     * 根据ID获取图表详情
     * 获取指定图表的详细信息（封装类）
     *
     * @param id 图表ID
     * @param request HTTP请求
     * @return 图表详情（封装类）
     */
    @GetMapping("/get/vo")
    @Operation(summary = "获取图表详情",
            description = """
                    根据ID获取图表的详细信息。

                    **权限要求：**
                    - 需要登录
                    - 公共图库：仅图表创建人或管理员可查看
                    - 私有空间：需要空间权限校验

                    **返回内容：**
                    - 图表基本信息（ID、名称、描述等）
                    - 文件URL（svgUrl、pictureUrl）
                    - 文件大小（svgSize、pngSize、picSize）
                    - 所属空间信息（spaceId）
                    """)
    public BaseResponse<DiagramVO> getDiagramVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        // 查询数据库
        Diagram diagram = diagramService.getById(id);
        ThrowUtils.throwIf(diagram == null, ErrorCode.NOT_FOUND_ERROR);
        // 空间权限校验
        Long spaceId = diagram.getSpaceId();
        if (spaceId != null ){
            User loginUser = userService.getLoginUser(request);
            spaceService.checkDiagramAuth(loginUser, diagram);
        }
        // 获取封装类
        return ResultUtils.success(diagramService.getDiagramVO(diagram, request));
    }

    /**
     * 分页获取图表列表（仅管理员可用）
     * 管理员专用的图表列表查询接口
     *
     * @param diagramQueryRequest 查询请求
     * @return 图表列表（分页）
     */
    @PostMapping("/list/page")
    @PreAuthorize("hasAuthority('admin')")
    @Operation(summary = "分页查询图表（管理员专用）",
            description = """
                    管理员专用的图表列表查询接口，可以查询所有图表。

                    **权限要求：**
                    - 仅限admin角色使用

                    **查询条件：**
                    - 支持按图表名称、ID、用户ID、空间ID等条件查询
                    - 支持分页查询
                    """)
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
     * 查询公共图库或私有空间的图表列表
     *
     * @param diagramQueryRequest 查询请求
     * @param request HTTP请求
     * @return 图表列表（封装类，分页）
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页查询图表列表",
            description = """
                    查询图表列表，支持公共图库和私有空间两种模式。

                    **查询模式：**
                    - **公共图库（spaceId为空）：**
                      - 查询所有不属于任何空间的图表
                      - 所有登录用户都可以查看
                    - **私有空间（spaceId不为空）：**
                      - 查询指定空间的图表
                      - 仅空间创建人可以查询

                    **权限要求：**
                    - 需要登录
                    - 私有空间：仅空间创建人可查询

                    **限制条件：**
                    - 每页最多20条（防止爬虫）
                    - 支持按名称、ID等条件筛选
                    """)
    public BaseResponse<Page<DiagramVO>> listDiagramVOByPage(@RequestBody DiagramQueryRequest diagramQueryRequest,
                                                               HttpServletRequest request) {
        long current = diagramQueryRequest.getCurrent();
        long size = diagramQueryRequest.getPageSize();
        Long spaceId = diagramQueryRequest.getSpaceId();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        // 公开图库
        if (spaceId == null){
            diagramQueryRequest.setNullSpaceId(true);
        }else {
            // 私有空间
            User loginUser = userService.getLoginUser(request);
            Space space = spaceService.getById(spaceId);
            ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
            if (!loginUser.getId().equals(space.getUserId())){
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有空间权限");
            }
        }
        // 查询数据库
        Page<Diagram> diagramPage = diagramService.page(new Page<>(current, size),
                diagramService.getQueryWrapper(diagramQueryRequest));
        // 获取封装类
        return ResultUtils.success(diagramService.getDiagramVOPage(diagramPage, request));
    }

    /**
     * 分页获取当前登录用户创建的图表列表
     * 查询自己创建的所有图表（包括公共图库和私有空间）
     *
     * @param diagramQueryRequest 查询请求
     * @param request HTTP请求
     * @return 我的图表列表（封装类，分页）
     */
    @PostMapping("/my/list/page/vo")
    @Operation(summary = "查询我的图表",
            description = """
                    查询当前登录用户创建的所有图表，包括公共图库和私有空间的图表。

                    **查询范围：**
                    - 包含用户在公共图库创建的图表
                    - 包含用户在自己私有空间创建的图表

                    **权限要求：**
                    - 需要登录
                    - 只能查询自己创建的图表

                    **限制条件：**
                    - 每页最多20条（防止爬虫）
                    - 支持按名称等条件筛选
                    """)
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
     * 编辑图表信息（给用户使用）
     *
     * @param diagramEditRequest
     * @param request
     * @return
     */
    @PostMapping("/edit")
    @Operation(summary = "编辑图表信息（给用户使用）")
    public BaseResponse<Boolean> editDiagram(@RequestBody DiagramEditRequest diagramEditRequest, HttpServletRequest request) {
        if (diagramEditRequest == null || diagramEditRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Diagram diagram = new Diagram();
        BeanUtils.copyProperties(diagramEditRequest, diagram);
        diagram.setName(diagramEditRequest.getTitle());
        // 数据校验
        diagramService.validDiagram(diagram, false);
        User loginUser = userService.getLoginUser(request);
        Long spaceId = diagramEditRequest.getSpaceId();
        // 判断是否存在
        long id = diagramEditRequest.getId();
        Diagram oldDiagram = diagramService.getById(id);
        ThrowUtils.throwIf(oldDiagram == null, ErrorCode.NOT_FOUND_ERROR);
        // 仅本人或管理员可编辑
        if (!oldDiagram.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 校验空间是否一致
        if (spaceId == null){
            if (oldDiagram.getSpaceId() != null){
                spaceId = oldDiagram.getSpaceId();
            }
        }else {
            if (ObjUtil.notEqual(spaceId, oldDiagram.getSpaceId())){
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "空间id不一致");
            }
        }
        // 操作数据库
        boolean result = diagramService.updateById(diagram);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }


    /**
     * 获取所有公共空间下的图表
     * @param pageRequest
     * @return
     */
    @PostMapping("/getDiagrams")
    public BaseResponse<Page<DiagramVO>> getByPage(@RequestBody DiagramQueryRequest pageRequest){
        Page<DiagramVO> resultPage = diagramService.getPublicDiagramsByPage(pageRequest);
        return ResultUtils.success(resultPage);
    }
    // endregion
}
