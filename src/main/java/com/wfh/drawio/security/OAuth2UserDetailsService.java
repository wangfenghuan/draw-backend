package com.wfh.drawio.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.mapper.UserMapper;
import com.wfh.drawio.model.entity.User;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 用户详情服务
 * 用于在OAuth2认证过程中加载用户信息
 *
 * @author fenghuanwang
 */
@Service
@Slf4j
public class OAuth2UserDetailsService {

    @Resource
    private UserMapper userMapper;

    /**
     * 加载OAuth2用户
     * 根据GitHub用户名查找或创建本地用户
     *
     * @param oAuth2User OAuth2用户信息
     * @return 本地用户实体（实现了UserDetails接口）
     */
    public User loadOAuth2User(OAuth2User oAuth2User) {
        if (oAuth2User == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "OAuth2用户信息不能为空");
        }

        // 从GitHub获取用户名
        String githubLogin = oAuth2User.getAttribute("login");
        if (githubLogin == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "无法获取GitHub用户名");
        }

        log.info("加载OAuth2用户: githubLogin={}", githubLogin);

        // 查找本地用户
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUserAccount, githubLogin);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            log.warn("OAuth2用户在本地数据库中不存在: githubLogin={}", githubLogin);
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR,
                    "用户不存在，请先通过OAuth2登录进行自动注册");
        }

        log.info("成功加载OAuth2用户: userId={}, userAccount={}", user.getId(), user.getUserAccount());
        return user;
    }
}
