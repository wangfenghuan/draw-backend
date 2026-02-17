package com.wfh.drawio.model.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Data;

/**
 * 发送验证码请求
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@Data
@Schema(name = "UserEmailCodeRequest", description = "发送验证码请求")
public class UserEmailCodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "用户账号(邮箱)", example = "wfh@qq.com")
    private String userAccount;


}
