package com.wfh.drawio.security.handler;

import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * @author fenghuanwang
 */
@Component
@Slf4j
public class LoginFailureHandler implements AuthenticationFailureHandler {
  @Override
  public void onAuthenticationFailure(
          HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {

    throw new BusinessException(ErrorCode.OPERATION_ERROR, "授权失败");
  }
}
