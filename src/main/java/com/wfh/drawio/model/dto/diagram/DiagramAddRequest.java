package com.wfh.drawio.model.dto.diagram;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建图表请求
 *
 * @author fenghuanwang
 */
@Data
public class DiagramAddRequest implements Serializable {

    /**
     * 标题
     */
    private String name;

    /**
     * 内容
     */
    private String diagramCode;

    /**
     * 图片url
     */
    private String pictureUrl;


    private static final long serialVersionUID = 1L;
}