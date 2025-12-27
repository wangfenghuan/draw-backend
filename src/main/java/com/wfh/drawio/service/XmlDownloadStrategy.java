package com.wfh.drawio.service;

import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.model.entity.Diagram;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * @Title: XmlDownloadStrategy
 * @Author wangfenghuan
 * @Package com.wfh.drawio.service
 * @Date 2025/12/24 19:36
 * @description:
 */
@Component
public class XmlDownloadStrategy implements DownloadStrategy{

    @Resource
    private DiagramService diagramService;

    @Override
    public void download(Long id, String fileName, HttpServletResponse response) {
        Diagram diagram = diagramService.getById(id);
        String diagramCode = diagram.getDiagramCode();
        try {
            byte[] bytes = diagramCode.getBytes(StandardCharsets.UTF_8);
            // 重置响应
            response.reset();
            response.setContentType("application/octet-stream");
            response.setContentLengthLong(bytes.length);
            // 设置文件名
            String encodedFileName = URLEncoder.encode(diagram.getName() + ".drawio", StandardCharsets.UTF_8);
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);

            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "下载错误");
        }
    }
}
