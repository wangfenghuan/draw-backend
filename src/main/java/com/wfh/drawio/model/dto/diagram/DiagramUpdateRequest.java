package com.wfh.drawio.model.dto.diagram;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新图表请求
 *
 * @author fenghuanwang
 */
@Data
public class DiagramUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

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