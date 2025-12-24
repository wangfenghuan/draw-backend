package com.wfh.drawio.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wfh.drawio.model.dto.diagram.DiagramQueryRequest;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.vo.DiagramVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


/**
 * 图表服务
 *
 * @author fenghuanwang
 */
public interface DiagramService extends IService<Diagram> {


    /**
     * 下载diagram
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
}
