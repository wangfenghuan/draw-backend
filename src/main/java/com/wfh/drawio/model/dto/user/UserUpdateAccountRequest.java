package com.wfh.drawio.model.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 用户更新账号请求（修改密码）
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@Data
@Schema(name = "UserUpdateAccountRequest", description = "用户更新账号请求")
public class UserUpdateAccountRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户账号(邮箱)", example = "wfh@qq.com")
    private String userAccount;

    @Schema(description = "邮箱验证码", example = "123456")
    private String emailCode;

    @Schema(description = "新密码", example = "12345678")
    private String newPassword;

    @Schema(description = "确认密码", example = "12345678")
    private String checkPassword;
}
