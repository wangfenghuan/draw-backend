package com.wfh.drawio.security;

import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.security.handler.LoginFailureHandler;
import com.wfh.drawio.security.handler.LoginSuccessHandler;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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
 * @author fenghuanwang
 */
@EnableWebSecurity
@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    @Resource
    private UserDetailsServiceImpl userDetailsService;

    @Resource
    private LoginSuccessHandler loginSuccessHandler;

    @Resource
    private LoginFailureHandler loginFailureHandler;

    @Resource
    @Qualifier("handlerExceptionResolver")
    private HandlerExceptionResolver resolver;

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity, SecurityContextRepository securityContextRepository) throws Exception {
        httpSecurity
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .securityContext(securityContext -> securityContext
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/user/register", "/user/login", "/user/logout", "/login",
                                "/login/oauth2/code/github").permitAll()
                        .requestMatchers("/doc.html", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/webjars/**").permitAll()
                        .requestMatchers("/excalidraw/**", "/static/**", "/public/**", "/yjs/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/user/createCaptcha").permitAll()
                        .requestMatchers("/diagram/getDiagrams").permitAll()
                        .requestMatchers("/material/list/page/vo").permitAll()
                        .requestMatchers("/internal/save", "/internal/auth").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            resolver.resolveException(request, response, null, new BusinessException(ErrorCode.NOT_LOGIN_ERROR));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            resolver.resolveException(request, response, null, new BusinessException(ErrorCode.NO_AUTH_ERROR));
                        })
                )
                .csrf(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .rememberMe(remember -> remember
                        .userDetailsService(userDetailsService)
                        .tokenValiditySeconds(60 * 60 * 24 * 7)
                )
                // OAuth2登录配置
                .oauth2Login(oauth2 -> oauth2
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
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

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}