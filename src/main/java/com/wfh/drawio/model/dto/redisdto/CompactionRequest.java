package com.wfh.drawio.model.dto.redisdto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 合并请求
 * @author fenghuanwang
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CompactionRequest {
    private Long roomId;
    private byte[] baseSnapshot;
    private List<byte[]> updates;
}