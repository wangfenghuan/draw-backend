package com.wfh.drawio.ai.tools;

/**
 * @Title: ToolResult
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.tools
 * @Date 2025/12/20 21:00
 * @description:
 */
public class ToolResult<T, R> {

    public final boolean success;
    public final R data;
    public final String error;

    private ToolResult(boolean success, R data, String error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T, R> ToolResult<T, R> success(R data, String message) {
        // 这里的 message 可以放在 data 中或者作为单独字段，这里复用 data 字段演示
        return new ToolResult<>(true, data, message);
    }

    public static <T, R> ToolResult<T, R> error(String error) {
        return new ToolResult<>(false, null, error);
    }

}
