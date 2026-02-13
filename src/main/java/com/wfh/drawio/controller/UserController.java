package com.wfh.drawio.controller;

import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTPayload;
import java.util.HashMap;
import java.util.Map;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Validator;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;


import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


import com.wfh.drawio.annotation.RateLimit;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.manager.RustFsManager;
import com.wfh.drawio.mapper.SysRoleMapper;
import com.wfh.drawio.model.dto.user.*;
import com.wfh.drawio.model.entity.SysAuthority;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.enums.RateLimitType;
import com.wfh.drawio.model.vo.LoginUserVO;
import com.wfh.drawio.model.vo.RoleAuthorityFlatVO;
import com.wfh.drawio.model.vo.RoleWithAuthoritiesVO;
import com.wfh.drawio.model.vo.UserVO;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户接口
 *
 * @author wangfenghuan
 * @from wangfenghuan
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private RustFsManager rustFsManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Value("${spring.security.oauth2.client.provider.github.authorization-uri}")
    private String baseUrl;

    @Value("${spring.security.oauth2.client.registration.github.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.github.scope}")
    private String scope;

    @Value("${spring.security.oauth2.client.registration.github.redirect-uri}")
    private String redirctUrl;


    // region 登录相关

    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册")
    @RateLimit(limitType = RateLimitType.USER, rate = 5, rateInterval = 10)
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String uuid = userRegisterRequest.getUuid();
        String key = String.format("captcha:uuid:%s", uuid);
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String userName = userRegisterRequest.getUserName();
        String captchaCode = userRegisterRequest.getCaptchaCode();
        // 先验证验证码是否正确
        String s = stringRedisTemplate.opsForValue().get(key);
        assert s != null;
        if (!captchaCode.toLowerCase(Locale.ROOT).equals(s.toLowerCase(Locale.ROOT))){
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "验证码错误");
        }
        if (userName == null){
            userName = "用户" + RandomUtil.randomString(5);
        }
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        long result = userService.userRegister(userAccount, userPassword, checkPassword, userName);
        return ResultUtils.success(result);
    }


    /**
     * 生成图片验证码(返回Map<uuid, 生成的base64验证码，后续uuid需要携带到注册接口>)
     * @param request
     * @return
     */
    @GetMapping("/createCaptcha")
    @Operation(summary = "生成图片验证码(返回Map<uuid, 生成的base64验证码，后续uuid需要携带到注册接口>)")
    public BaseResponse<Map<String, String>> createCaptcha(HttpServletRequest request) {
        ShearCaptcha captcha = CaptchaUtil.createShearCaptcha(200, 100, 4, 2);
        String imageBase64 = captcha.getImageBase64();
        String code = captcha.getCode();
        // 讲解过存储到Redis中，1min有效
        UUID uuid = UUID.randomUUID();
        String key = String.format("captcha:uuid:%s", uuid);
        stringRedisTemplate.opsForValue().set(key, code, 60, TimeUnit.SECONDS);
        Map<String, String> resMap = new HashMap<>();
        resMap.put(uuid.toString(), imageBase64);
        return ResultUtils.success(resMap);
    }


    /**
     * 用户登录
     *
     * @param userLoginRequest
     * @param request
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request, HttpServletResponse response) {
        if (userLoginRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request, response);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    @PostMapping("/logout")
    @Operation(summary = "用户推出登录")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }


    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    @GetMapping("/get/login")
    @Operation(summary = "获取当前登录用户")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        LoginUserVO loginUserVO = userService.getLoginUserVO(user);
        
        // 使用 Hutool 生成 JWT Token
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId());
        payload.put(JWTPayload.EXPIRES_AT, System.currentTimeMillis() + 1000 * 60 * 60 * 24 * 7); // 7天过期
        
        String token = JWTUtil.createToken(payload, "wfh-drawio-jwt-secret".getBytes());
        loginUserVO.setToken(token);
        
        return ResultUtils.success(loginUserVO);
    }

    // endregion

    // region 增删改查

    /**
     * 创建用户
     *
     * @param userAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @Operation(summary = "创建用户")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest, HttpServletRequest request) {
        Long userId = userService.addUserByAdmin(userAddRequest);
        return ResultUtils.success(userId);
    }

    /**
     * 删除用户
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @Operation(summary = "删除用户")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     *
     * @param userUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @Operation(summary = "更新用户")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest,
            HttpServletRequest request) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取用户（仅管理员）
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @Operation(summary = "根据 id 获取用户（仅管理员）")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<User> getUserById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据 id 获取包装类")
    public BaseResponse<UserVO> getUserVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getLoginUser(request);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        UserVO userVO = userService.getUserVO(user);
        return ResultUtils.success(userVO);
    }

    /**
     * 分页获取用户列表（仅管理员）
     *
     * @param userQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    @Operation(summary = "分页获取用户列表（仅管理员）")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest,
            HttpServletRequest request) {
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        return ResultUtils.success(userPage);
    }

    /**
     * 分页获取用户封装列表
     *
     * @param userQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页获取用户封装列表")
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest,
            HttpServletRequest request) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userService.getUserVO(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return ResultUtils.success(userVOPage);
    }

    // endregion

    /**
     * 上传用户头像图片
     *
     * @param file
     * @param request
     * @return
     */
    @PostMapping("/upload/image")
    @Operation(summary = "上传头像图片")
    public BaseResponse<String> uploadAvataImage(@RequestPart("file") MultipartFile file,
                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "文件不能为空");

        // 校验文件大小（最大5MB）
        long maxSize = 5 * 1024 * 1024L;
        ThrowUtils.throwIf(file.getSize() > maxSize, ErrorCode.PARAMS_ERROR, "文件大小不能超过5MB");

        // 校验文件类型
        String contentType = file.getContentType();
        ThrowUtils.throwIf(contentType == null || !contentType.startsWith("image/"),
                ErrorCode.PARAMS_ERROR, "只能上传图片文件");

        User loginUser = userService.getLoginUser(request);
        // 文件目录：feedback/{userId}/{filename}
        String uuid = RandomStringUtils.randomAlphanumeric(8);
        String filename = uuid + "-" + file.getOriginalFilename();
        String filepath = String.format("userAvata/%s/%s", loginUser.getId(), filename);

        try {
            // 上传文件
            String fileUrl = rustFsManager.putObject(filepath, file.getInputStream());
            // 返回可访问地址
            return ResultUtils.success(fileUrl);
        } catch (Exception e) {
            log.error("feedback image upload error, filepath = " + filepath, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "上传失败");
        }
    }

    /**
     * 更新个人信息
     *
     * @param userUpdateMyRequest
     * @param request
     * @return
     */
    @PostMapping("/update/my")
    @Operation(summary = "更新个人信息")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest,
            HttpServletRequest request) {
        if (userUpdateMyRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User loginUser = userService.getLoginUser(request);
        User user = BeanUtil.copyProperties(userUpdateMyRequest, User.class);
        user.setUserName(userUpdateMyRequest.getUserName());
        user.setId(loginUser.getId());
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 查询所有的角色以及其所对应的权限信息
     * @return
     */
    @Operation(summary = "查询所有的角色以及对应的权限")
    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("/getAuth")
    public BaseResponse< List<RoleWithAuthoritiesVO>> getAllRoleAndAuth(){
        List<RoleWithAuthoritiesVO> resList = userService.getRoleWithAuthoritiesVOS();
        return ResultUtils.success(resList);
    }

    /**
     * 修改用户角色
     *
     * @param userRoleUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update/roles")
    @Operation(summary = "修改用户角色")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> updateUserRoles(@RequestBody UserRoleUpdateRequest userRoleUpdateRequest,
                                                  HttpServletRequest request) {
        if (userRoleUpdateRequest == null || userRoleUpdateRequest.getUserId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.updateUserRoles(userRoleUpdateRequest.getUserId(), userRoleUpdateRequest.getRoleIds());
        return ResultUtils.success(result);
    }

    /**
     * 修改角色权限
     *
     * @param roleAuthorityUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/role/update/authorities")
    @Operation(summary = "修改角色权限")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> updateRoleAuthorities(@RequestBody RoleAuthorityUpdateRequest roleAuthorityUpdateRequest,
                                                         HttpServletRequest request) {
        if (roleAuthorityUpdateRequest == null || roleAuthorityUpdateRequest.getRoleId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.updateRoleAuthorities(roleAuthorityUpdateRequest.getRoleId(),
                roleAuthorityUpdateRequest.getAuthorityIds());
        return ResultUtils.success(result);
    }
}
