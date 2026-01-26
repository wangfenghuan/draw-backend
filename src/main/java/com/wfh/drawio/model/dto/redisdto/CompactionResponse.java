package com.wfh.drawio.model.dto.redisdto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 合并响应
 * @author fenghuanwang
 */
@Data
@NoArgsConstructor
public class CompactionResponse {
    private boolean success;
    private byte[] merged;
    private String message;
}