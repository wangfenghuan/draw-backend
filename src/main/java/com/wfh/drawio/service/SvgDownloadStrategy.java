package com.wfh.drawio.service;

import com.wfh.drawio.model.entity.Diagram;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

/**
 * @Title: XmlDownloadStrategy
 * @Author wangfenghuan
 * @Package com.wfh.drawio.service
 * @Date 2025/12/24 19:36
 * @description:
 */
@Component
public class SvgDownloadStrategy implements DownloadStrategy{

    @Resource
    private DiagramService diagramService;

    @Override
    public void download(Long id, String fileName, HttpServletResponse response) {
        Diagram diagram = diagramService.getById(id);
        String svgUrl = diagram.getSvgUrl();
        diagramService.download(svgUrl, fileName, response);
    }
}
