package com.wfh.drawio.security;

import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.Arrays;
import java.util.List;

/**
 * @Title: SecurityConfig
 * @Author wangfenghuan
 * @Package com.wfh.drawio.security
 * @Date 2026/1/9 13:44
 * @description: Security配置类
 */
@EnableWebSecurity
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Resource
    private UserDetailsServiceImpl userDetailsService;

    @Resource
    private ApplicationContext applicationContext;

    @Resource
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver resolver;

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity, SecurityContextRepository securityContextRepository) throws Exception {
        // 配置 SecurityContextRepository,将 SecurityContext 持久化到 HttpSession
        // 这样 Spring Session 可以自动将 Session 同步到 Redis
        httpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 设置 SecurityContextRepository
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true)
                )
                .authorizeHttpRequests(auth -> auth
                        // 注册和登录接口
                        .requestMatchers(
                                "/user/register",
                                "/user/login",
                                "/user/logout"
                        ).permitAll()
                        // 接口文档
                        .requestMatchers(
                                "/doc.html",        // Knife4j 接口文档入口
                                "/swagger-ui/**",   // Swagger UI 页面
                                "/swagger-ui.html", // Swagger UI 老版入口
                                "/v3/api-docs/**",  // OpenAPI 3.0 描述数据 (JSON)
                                "/webjars/**"       // Swagger 依赖的静态资源 (JS/CSS)
                        ).permitAll()
                        .requestMatchers("/excalidraw/**").permitAll()
                        // 静态资源
                        .requestMatchers("/static/**", "/public/**").permitAll()
                        // WebSocket 路径
                        .requestMatchers("/yjs/**").permitAll()
                        // 处理跨域请求的预检请求 (OPTIONS)
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        // 当用户未登录访问受保护资源时，不要重定向，而是返回 JSON 401
                        .authenticationEntryPoint((request, response, authException) -> {
                            resolver.resolveException(request, response, null, new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
                        })
                        // 当用户权限不足时返回 JSON 403
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            resolver.resolveException(request, response, null, new BusinessException(ErrorCode.NO_AUTH_ERROR));
                        })
                )
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .rememberMe(remember -> remember
                        .userDetailsService(userDetailsService)
                        .tokenValiditySeconds(60 * 60 * 24 * 7) // 7天有效
                )
                .userDetailsService(userDetailsService);
        return httpSecurity.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * 自定义方法安全表达式处理器
     * 用于支持自定义的权限检查方法（如 hasSpaceAuthority、hasRoomAuthority）
     */
    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler(
            ApplicationContext applicationContext,
            SpaceSecurityService spaceSecurityService,
            RoomSecurityService roomSecurityService) {
        CustomMethodSecurityExpressionHandler handler = new CustomMethodSecurityExpressionHandler(
                spaceSecurityService,
                roomSecurityService,
                applicationContext);
        return handler;
    }

    /**
     * CORS 配置源
     * 针对 Session 模式的配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 允许的前端域名 (必须是具体的，不能写 "*")
        configuration.setAllowedOriginPatterns(List.of("*"));

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));

        // 【关键】是否允许发送 Cookie/凭证
        // 因为你用了 Session，这里必须是 true
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}