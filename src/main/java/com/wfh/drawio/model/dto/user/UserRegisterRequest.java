package com.wfh.drawio.model.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

/**
 * 用户注册请求体
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@Data
@Schema(name = "UserRegisterRequest", description = "用户注册请求")
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 3191241716373120793L;

    @Schema(description = "用户账号", example = "admin")
    private String userAccount;

    @Schema(description = "用户密码", example = "********")
    private String userPassword;

    @Schema(description = "确认密码", example = "********")
    private String checkPassword;

    @Schema(description = "用户昵称", example = "张三")
    private String userName;

    @Schema(description = "图形验证码")
    private String captchaCode;

    @Schema(description = "验证码uuid")
    private String uuid;
}
