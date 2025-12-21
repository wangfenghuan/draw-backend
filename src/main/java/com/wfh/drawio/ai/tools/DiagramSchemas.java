package com.wfh.drawio.ai.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @Title: DiagramSchemas
 * @Author wangfenghuan
 * @Date 2025/12/20 20:54
 * @description: 定义 AI 调用的工具参数结构 (Input Schemas)
 */
public class DiagramSchemas {

    // Tool: display_diagram
    @Data
    public static class CreateDiagramRequest {
        @NotBlank(message = "XML content is required")
        @JsonProperty("xml")
        private String xml;

        public CreateDiagramRequest() {}

        public CreateDiagramRequest(String xml) {
            this.xml = xml;
        }

    }

    @Data
    public static class EditOperation {
        @NotBlank(message = "Operation type is required")
        // 【修正点】修复了原代码中的换行字符串语法错误
        @Pattern(regexp = "^(update|add|delete)$",
                message = "Operation type must be update, add, or delete")
        @JsonProperty("type")
        private String type;

        @NotBlank(message = "cell_id is required")
        @JsonProperty("cell_id")
        private String cellId;

        // update/add 操作必填，delete 操作选填
        @JsonProperty("new_xml")
        private String newXml;

        public EditOperation() {}

        public EditOperation(String type, String cellId, String newXml) {
            this.type = type;
            this.cellId = cellId;
            this.newXml = newXml;
        }

    }

    // Tool: edit_diagram
    @Data
    public static class EditDiagramRequest {
        @NotEmpty(message = "Operations array is required")
        @JsonProperty("operations")
        private List<EditOperation> operations;

        public EditDiagramRequest() {
            this.operations = new ArrayList<>();
        }

        public EditDiagramRequest(List<EditOperation> operations) {
            this.operations = operations;
        }

    }

    // Tool: append_diagram
    @Data
    public static class AppendDiagramRequest {
        @NotBlank(message = "XML content is required")
        @JsonProperty("xml")
        private String xml;

        public AppendDiagramRequest() {}

        public AppendDiagramRequest(String xml) {
            this.xml = xml;
        }

    }
}