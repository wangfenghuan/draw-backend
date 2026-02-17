package com.wfh.drawio.service.impl;

import com.wfh.drawio.service.UserEmailService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 用户邮件服务实现
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@Service
@Slf4j
public class UserEmailServiceImpl implements UserEmailService {

    @Resource
    private JavaMailSender javaMailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendVerificationCode(String email, String code) {
        sendHtmlEmail(email, "Intellidraw 验证码", code);
    }

    private void sendHtmlEmail(String email, String subject, String code) {
        try {
            jakarta.mail.internet.MimeMessage mimeMessage = javaMailSender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(mimeMessage, true);
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject(subject);
            
            String htmlContent = String.format(
                "<div style=\"font-family: Arial, sans-serif; padding: 20px; border: 1px solid #eee; border-radius: 8px; max-width: 500px;\">" +
                "<h2 style=\"color: #333;\">IntelliDraw 智能绘图</h2>" +
                "<p style=\"font-size: 16px; color: #555;\">您的验证码是：</p>" +
                "<div style=\"font-size: 32px; font-weight: bold; color: #007bff; letter-spacing: 4px; margin: 20px 0;\">%s</div>" +
                "<p style=\"font-size: 14px; color: #888;\">有效期 5 分钟，请勿泄露给他人。</p>" +
                "</div>", code);
            
            helper.setText(htmlContent, true);
            javaMailSender.send(mimeMessage);
            log.info("发送邮件成功，email: {}, subject: {}, code: {}", email, subject, code);
        } catch (Exception e) {
            log.error("发送邮件失败，email: {}, subject: {}, code: {}", email, subject, code, e);
            throw new RuntimeException("发送邮件失败，请稍后重试");
        }
    }
}
