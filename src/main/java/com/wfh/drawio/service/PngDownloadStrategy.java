package com.wfh.drawio.service;

import com.wfh.drawio.model.entity.Diagram;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
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
public class PngDownloadStrategy implements DownloadStrategy{


    @Resource
    private DiagramService diagramService;

    @Override
    public void download(Long id, String fileName, HttpServletResponse response) {
        Diagram diagram = diagramService.getById(id);
        String pictureUrl = diagram.getPictureUrl();
        diagramService.download(pictureUrl, fileName, response);
    }

}
