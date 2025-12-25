package com.wfh.drawio.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * @Title: DownloadStrategy
 * @Author wangfenghuan
 * @Package com.wfh.drawio.service
 * @Date 2025/12/24 19:33
 * @description: 通用下载策略
 */
public interface DownloadStrategy {

    /**
     * 下载
     * @param id
     * @param fileName
     * @param response
     */
    void download(Long id, String fileName, HttpServletResponse response);

}
