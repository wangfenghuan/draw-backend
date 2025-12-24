package com.wfh.drawio.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.diagram.DiagramQueryRequest;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.mapper.DiagramMapper;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.vo.DiagramVO;
import com.wfh.drawio.service.DiagramService;
import com.wfh.drawio.service.UserService;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 图表服务实现
 *
 */
@Service
@Slf4j
public class DiagramServiceImpl extends ServiceImpl<DiagramMapper, Diagram> implements DiagramService {

    @Resource
    private UserService userService;


    /**
     * 下载文件
     * @param remoteUrl
     * @param fileName
     * @param response
     */
    @Override
    public void download(String remoteUrl, String fileName, HttpServletResponse response) {
        HttpURLConnection connection = null;
        InputStream remoteInputStream = null;

        try {
            // 1. 建立连接
            URL url = new URL(remoteUrl);
            connection = (HttpURLConnection) url.openConnection();
            // 关键：设置超时，避免远程服务挂死导致本地线程池耗尽
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(60000);
            // 2. 检查远程状态
            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                // 如果远程文件不存在或报错，直接返回错误给前端
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "远程文件无法获取，状态码: " + responseCode);
                return;
            }
            // 3. 获取远程文件元数据
            String contentType = connection.getContentType();
            long contentLength = connection.getContentLengthLong();
            String finalFileName = (fileName != null && !fileName.isEmpty()) ? fileName : extractFileNameFromUrl(remoteUrl);
            String encodedFileName = URLEncoder.encode(finalFileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.reset();
            response.setContentType(contentType != null ? contentType : "application/octet-stream");
            if (contentLength > 0) {
                response.setContentLengthLong(contentLength);
            }
            // 设为附件下载模式
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedFileName);
            // 核心：流对接 (输入流 -> 输出流)
            remoteInputStream = connection.getInputStream();
            StreamUtils.copy(remoteInputStream, response.getOutputStream());
            response.getOutputStream().flush();

        } catch (Exception e) {
            // 处理下载过程中的网络中断等异常
            // 注意：如果响应头已经发送，这里再 sendError 可能无效，通常只记录日志
            System.err.println("下载文件出错: " + e.getMessage());
        } finally {
            // 8. 资源清理
            if (remoteInputStream != null) {
                try {
                    remoteInputStream.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (connection != null) {
                connection.disconnect();
            }
        }
    }



    /**
     * 从URL截取文件名
     * @param url
     * @return
     */
    private String extractFileNameFromUrl(String url) {
        try {
            // 简单的截取逻辑，实际场景可能需要处理 URL 参数 (?ver=1.0)
            String path = new URL(url).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            return name.isEmpty() ? "unknown_file" : name;
        } catch (Exception e) {
            return "download_file";
        }
    }

    /**
     * 校验数据
     *
     * @param diagram
     * @param add      对创建的数据进行校验
     */
    @Override
    public void validDiagram(Diagram diagram, boolean add) {
        ThrowUtils.throwIf(diagram == null, ErrorCode.PARAMS_ERROR);
        String name = diagram.getName();
        String diagramCode = diagram.getDiagramCode();
        Long userId = diagram.getUserId();
        // 创建数据时，参数不能为空
        if (add) {
            ThrowUtils.throwIf(StringUtils.isBlank(name), ErrorCode.PARAMS_ERROR);
            ThrowUtils.throwIf(ObjectUtils.isEmpty(userId), ErrorCode.PARAMS_ERROR);
        }
        // 修改数据时，有参数则校验
        if (StringUtils.isNotBlank(name) || StringUtils.isNotBlank(diagramCode)) {
            ThrowUtils.throwIf(name.length() > 80, ErrorCode.PARAMS_ERROR, "标题过长");
        }
    }

    /**
     * 获取查询条件
     *
     * @param diagramQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Diagram> getQueryWrapper(DiagramQueryRequest diagramQueryRequest) {
        QueryWrapper<Diagram> queryWrapper = new QueryWrapper<>();
        if (diagramQueryRequest == null) {
            return queryWrapper;
        }
        Long id = diagramQueryRequest.getId();
        String name = diagramQueryRequest.getTitle();
        String searchText = diagramQueryRequest.getSearchText();
        Long userId = diagramQueryRequest.getUserId();
        // 从多字段中搜索
        if (StringUtils.isNotBlank(searchText)) {
            // 需要拼接查询条件
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("name", searchText));
        }
        // 模糊查询
        queryWrapper.like(StringUtils.isNotBlank(name), "name", name);
        // 精确查询
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        return queryWrapper;
    }

    /**
     * 获取图表封装
     *
     * @param diagram
     * @param request
     * @return
     */
    @Override
    public DiagramVO getDiagramVO(Diagram diagram, HttpServletRequest request) {
        // 对象转封装类
        return DiagramVO.objToVo(diagram);
    }

    /**
     * 分页获取图表封装
     *
     * @param diagramPage
     * @param request
     * @return
     */
    @Override
    public Page<DiagramVO> getDiagramVOPage(Page<Diagram> diagramPage, HttpServletRequest request) {
        List<Diagram> diagramList = diagramPage.getRecords();
        Page<DiagramVO> diagramVOPage = new Page<>(diagramPage.getCurrent(), diagramPage.getSize(), diagramPage.getTotal());
        if (CollUtil.isEmpty(diagramList)) {
            return diagramVOPage;
        }
        // 对象列表 => 封装对象列表
        List<DiagramVO> diagramVOList = diagramList.stream().map(DiagramVO::objToVo).collect(Collectors.toList());
        diagramVOPage.setRecords(diagramVOList);
        return diagramVOPage;
    }

}
