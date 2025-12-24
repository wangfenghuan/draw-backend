package com.wfh.drawio.model.dto.diagram;

import com.wfh.drawio.model.dto.file.UploadFileRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @Title: DiagramUploadRequest
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.diagram
 * @Date 2025/12/24 15:57
 * @description:
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class DiagramUploadRequest extends UploadFileRequest implements Serializable {

    private Long diagramId;

    private Long userId;

}
