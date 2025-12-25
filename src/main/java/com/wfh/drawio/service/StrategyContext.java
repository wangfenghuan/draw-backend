package com.wfh.drawio.service;

import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;

/**
 * @Title: StrategyContext
 * @Author wangfenghuan
 * @Package com.wfh.drawio.service
 * @Date 2025/12/24 19:37
 * @description:
 */
@Data
public class StrategyContext {

    private DownloadStrategy downloadStrategy;

    /**
     * 执行下载
     * @param id
     * @param fileName
     * @param response
     */
    public void execDownload(Long id, String fileName, HttpServletResponse response){
        if (downloadStrategy == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR);
        }
        downloadStrategy.download(id, fileName, response);
    }

}
