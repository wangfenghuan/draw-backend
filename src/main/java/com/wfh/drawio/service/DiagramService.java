package com.wfh.drawio.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wfh.drawio.model.dto.diagram.DiagramAddRequest;
import com.wfh.drawio.model.dto.diagram.DiagramQueryRequest;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.enums.FileUploadBizEnum;
import com.wfh.drawio.model.vo.DiagramVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;


/**
 * 图表服务
 *
 * @author fenghuanwang
 */
public interface DiagramService extends IService<Diagram> {


    boolean tryAcquireLock(String roomName);

    /**
     * 下载
     * @param remoteUrl
     * @param fileName
     * @param response
     */
    void download(String remoteUrl, String fileName, HttpServletResponse response);

    /**
     * 校验数据
     *
     * @param diagram
     * @param add 对创建的数据进行校验
     */
    void validDiagram(Diagram diagram, boolean add);

    /**
     * 获取查询条件
     *
     * @param diagramQueryRequest
     * @return
     */
    QueryWrapper<Diagram> getQueryWrapper(DiagramQueryRequest diagramQueryRequest);
    
    /**
     * 获取图表封装
     *
     * @param diagram
     * @param request
     * @return
     */
    DiagramVO getDiagramVO(Diagram diagram, HttpServletRequest request);

    /**
     * 分页获取图表封装
     *
     * @param diagramPage
     * @param request
     * @return
     */
    Page<DiagramVO> getDiagramVOPage(Page<Diagram> diagramPage, HttpServletRequest request);

    /**
     * 上传图表文件并更新空间额度（带事务）
     *
     * @param diagramId 图表ID
     * @param spaceId 空间ID
     * @param fileUrl 文件URL
     * @param fileSize 文件大小
     * @param extension 文件扩展名
     * @param loginUser 登录用户
     */
    void uploadDiagramWithQuota(Long diagramId, Long spaceId, String fileUrl, Long fileSize, String extension, User loginUser);

    /**
     * 删除图表并释放额度（带事务）
     *
     * @param id 图表ID
     */
    void deleteDiagramWithQuota(Long id);

    /**
     * 创建图表并更新空间额度（带事务）
     *
     * @param diagramAddRequest
     * @param loginUser
     * @return
     */
    Long addDiagramWithQuota(DiagramAddRequest diagramAddRequest, User loginUser);

    /**
     * 校验图表文件
     *
     * @param multipartFile 上传的文件
     * @param fileUploadBizEnum 业务类型
     */
    void validDiagramFile(MultipartFile multipartFile, FileUploadBizEnum fileUploadBizEnum);

    /**
     * 分页获取所有公共空间的图表（带多级缓存）
     * 缓存策略: Caffeine(L1) -> Redis(L2) -> DB
     *
     * @param diagramQueryRequest 查询请求
     * @return 图表列表（封装类，分页）
     */
    Page<DiagramVO> getPublicDiagramsByPage(DiagramQueryRequest diagramQueryRequest);

    /**
     * 根据图表ID和文件类型下载图表文件
     *
     * @param diagramId 图表ID
     * @param type 文件类型（SVG、PNG、XML）
     * @param fileName 下载后的文件名（可选）
     * @param response HTTP响应对象
     */
    void downloadDiagramFile(Long diagramId, String type, String fileName, HttpServletResponse response);
}
