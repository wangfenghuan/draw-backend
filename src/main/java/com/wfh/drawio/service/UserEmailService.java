package com.wfh.drawio.service;
/**
 * 用户邮件服务
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
public interface UserEmailService {
    /**
     * 发送验证码
     *
     * @param email
     * @param code
     */
    void sendVerificationCode(String email, String code);
}
