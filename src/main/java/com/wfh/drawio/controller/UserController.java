package com.wfh.drawio.controller;

import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTPayload;
import java.util.HashMap;
import java.util.Map;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.ShearCaptcha;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;


import java.util.*;
import java.util.concurrent.TimeUnit;


import com.wfh.drawio.annotation.RateLimit;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.manager.RustFsManager;
import com.wfh.drawio.model.dto.user.*;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.entity.SysAuthority;
import com.wfh.drawio.mapper.SysAuthorityMapper;
import com.wfh.drawio.model.enums.RateLimitType;
import com.wfh.drawio.model.vo.LoginUserVO;
import com.wfh.drawio.model.vo.RoleWithAuthoritiesVO;
import com.wfh.drawio.model.vo.UserVO;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * @Title: UserController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2025/12/23 16:48
 * @description: 用户接口
 */
@Tag(name = "userController", description = "用户注册、登录、信息管理接口")
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private SysAuthorityMapper sysAuthorityMapper;

    @Resource
    private RustFsManager rustFsManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private PasswordEncoder passwordEncoder;


    // region 登录相关

    /**
     * 用户注册
     *
     * @param userRegisterRequest 用户注册请求（包含账号、密码、验证码等）
     * @return 新创建的用户ID
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册",
            description = """
                    用户邮箱注册账号。

                    **功能说明：**
                    - 使用邮箱作为账号注册
                    - 需要邮箱验证码验证
                    - 支持邀请码机制

                    **校验规则：**
                    - 账号长度不少于4位
                    - 密码长度不少于8位
                    - 两次密码输入必须一致
                    - 需要有效的邮箱验证码""")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        if (userRegisterRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String uuid = userRegisterRequest.getUuid();
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String userName = userRegisterRequest.getUserName();
        String inviteCode = userRegisterRequest.getInviteCode();
        // String captchaCode = userRegisterRequest.getCaptchaCode();
        
        // 校验邮箱验证码
        String emailCode = userRegisterRequest.getEmailCode();
        if (StringUtils.isBlank(emailCode)) {
             throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱验证码不能为空");
        }
        String emailKey = String.format("email:code:%s", userAccount);
        String cacheEmailCode = stringRedisTemplate.opsForValue().get(emailKey);
        if (StringUtils.isBlank(cacheEmailCode) || !cacheEmailCode.equals(emailCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱验证码错误或已过期");
        }
        // 删除邮箱验证码
        stringRedisTemplate.delete(emailKey);
        
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
        long result = userService.userRegister(userAccount, userPassword, checkPassword, userName, inviteCode);
        return ResultUtils.success(result);
    }


    /**
     * 生成图片验证码
     *
     * @param request HTTP请求
     * @return Map<验证码UUID, Base64图片数据>
     */
    @GetMapping("/createCaptcha")
    @Operation(summary = "生成图片验证码",
            description = """
                    生成图形验证码用于注册校验。

                    **返回内容：**
                    - key：验证码UUID
                    - value：Base64编码的验证码图片

                    **有效期：**
                    - 验证码60秒内有效""")
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
     * @param userLoginRequest 用户登录请求（账号和密码）
     * @param request          HTTP请求
     * @param response         HTTP响应（用于设置Session Cookie）
     * @return 登录用户信息（包含JWT Token）
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录",
            description = """
                    用户使用账号密码登录。

                    **功能说明：**
                    - 验证账号密码
                    - 创建Session会话
                    - 返回JWT Token（用于WebSocket认证）

                    **返回内容：**
                    - 用户基本信息
                    - JWT Token（7天有效期）""")
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
     * 用户注销登录
     *
     * @param request HTTP请求
     * @return 是否注销成功
     */
    @PostMapping("/logout")
    @Operation(summary = "用户退出登录",
            description = """
                    用户退出登录，清除Session会话。

                    **功能说明：**
                    - 清除当前用户的Session
                    - 清除相关登录状态""")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        if (request == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }


    /**
     * 获取当前登录用户信息
     *
     * @param request HTTP请求
     * @return 登录用户信息（包含JWT Token）
     */
    @GetMapping("/get/login")
    @Operation(summary = "获取当前登录用户",
            description = """
                    获取当前登录用户的详细信息。

                    **返回内容：**
                    - 用户基本信息
                    - JWT Token（用于WebSocket认证，7天有效期）""")
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

    /**
     * 发送注册验证码
     *
     * @param email
     * @return
     */
    @Resource
    private com.wfh.drawio.service.UserEmailService userEmailService;

    /**
     * 发送注册验证码
     *
     * @param userEmailCodeRequest 邮箱验证码请求（包含邮箱地址）
     * @return 是否发送成功
     */
    @PostMapping("/send-register-code")
    @Operation(summary = "发送注册验证码",
            description = """
                    向指定邮箱发送注册验证码。

                    **功能说明：**
                    - 发送6位数字验证码到邮箱
                    - 验证码5分钟内有效

                    **限流规则：**
                    - 同一IP每分钟最多发送1次

                    **邮箱格式校验：**
                    - 必须是有效的邮箱格式""")
    @RateLimit(limitType = RateLimitType.IP, rate = 1, rateInterval = 60)
    public BaseResponse<Boolean> sendRegisterCode(@RequestBody UserEmailCodeRequest userEmailCodeRequest) {
        if (userEmailCodeRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String email = userEmailCodeRequest.getUserAccount();
        if (StringUtils.isBlank(email)) {
             throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱不能为空");
        }
        // 校验邮箱格式
        if (!email.matches("^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$")) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
        }

        // 生成6位随机验证码
        String code = RandomUtil.randomNumbers(6);
        
        // 统一使用一个key
        String key = String.format("email:code:%s", email);
        
        // 发送邮件
        userEmailService.sendVerificationCode(email, code);
        
        // 存储到Redis，有效期5分钟
        stringRedisTemplate.opsForValue().set(key, code, 5, TimeUnit.MINUTES);
        
        return ResultUtils.success(true);
    }

    /**
     * 查询用户剩余的AI调用次数
     *
     * @param request HTTP请求
     * @return 包含日限额、奖励额度和总额度的Map
     */
    @GetMapping("/get/ai/quota")
    @Operation(summary = "查询用户的AI调用额度",
            description = """
                    查询当前用户的AI调用额度信息。

                    **返回内容：**
                    - dailyQuota：每日额度（默认5次/天）
                    - bonusQuota：永久奖励额度
                    - totalQuota：总额度

                    **权限要求：**
                    - 需要登录""")
    public BaseResponse<Map<String, Integer>> getUserAiQuota(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        String userId = loginUser.getId().toString();
        
        // 1. 获取每日额度
        String callCountKey = com.wfh.drawio.constant.RedisPrefixConstant.USER_AI_CALL_COUNT + userId;
        String callCountStr = stringRedisTemplate.opsForValue().get(callCountKey);
        // 如果没有记录则默认为5次
        int dailyCount = (callCountStr != null) ? Integer.parseInt(callCountStr) : 5;
        if (dailyCount < 0) dailyCount = 0;

        // 2. 获取永久奖励额度
        String bonusCountKey = com.wfh.drawio.constant.RedisPrefixConstant.USER_AI_BONUS_COUNT + userId;
        String bonusCountStr = stringRedisTemplate.opsForValue().get(bonusCountKey);
        int bonusCount = (bonusCountStr != null) ? Integer.parseInt(bonusCountStr) : 0;
        if (bonusCount < 0) bonusCount = 0;

        Map<String, Integer> result = new HashMap<>();
        result.put("dailyQuota", dailyCount);
        result.put("bonusQuota", bonusCount);
        result.put("totalQuota", dailyCount + bonusCount);
        
        return ResultUtils.success(result);
    }

    /**
     * 更新账号信息（修改密码/换绑邮箱）
     *
     * @param userUpdateAccountRequest 账号更新请求
     * @param request                  HTTP请求
     * @return 是否更新成功
     */
    @PostMapping("/update/account")
    @Operation(summary = "更新账号信息",
            description = """
                    更新账号信息，支持修改密码或换绑邮箱。

                    **场景1：修改密码**
                    - 需要验证当前邮箱的验证码
                    - 新密码不少于8位
                    - 两次密码输入必须一致

                    **场景2：换绑邮箱**
                    - 需要验证新邮箱的验证码
                    - 新邮箱不能已被其他用户绑定

                    **权限要求：**
                    - 需要登录""")
    public BaseResponse<Boolean> updateAccount(@RequestBody UserUpdateAccountRequest userUpdateAccountRequest,
            HttpServletRequest request) {
        if (userUpdateAccountRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        String userAccount = userUpdateAccountRequest.getUserAccount(); // 邮箱
        String emailCode = userUpdateAccountRequest.getEmailCode();
        String newPassword = userUpdateAccountRequest.getNewPassword();
        String checkPassword = userUpdateAccountRequest.getCheckPassword();

        if (StringUtils.isBlank(emailCode)) {
             throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码不能为空");
        }

        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        User user = new User();
        user.setId(loginUser.getId());

        // 场景1：修改密码 (密码字段不为空)
        if (StringUtils.isNotBlank(newPassword) && StringUtils.isNotBlank(checkPassword)) {
            if (!newPassword.equals(checkPassword)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
            }
            if (newPassword.length() < 8) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于8位");
            }
            // 验证码验证：需验证当前账号的验证码
            String key = String.format("email:code:%s", loginUser.getUserAccount());
            String cacheCode = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.isBlank(cacheCode) || !cacheCode.equals(emailCode)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
            }
            // 删除验证码
            stringRedisTemplate.delete(key);

             // 加密密码
            String encodedPassword = passwordEncoder.encode(newPassword);
            user.setUserPassword(encodedPassword);
        } 
        // 场景2：换绑邮箱 (密码为空，userAccount为新邮箱)
        else if (StringUtils.isNotBlank(userAccount)) {
            // 校验新邮箱格式
             if (!userAccount.matches("^[a-zA-Z0-9_.-]+@[a-zA-Z0-9-]+(\\.[a-zA-Z0-9-]+)*\\.[a-zA-Z0-9]{2,6}$")) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "邮箱格式不正确");
            }
            // 检查新邮箱是否已被使用
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = userService.count(queryWrapper);
            if (count > 0) {
                 throw new BusinessException(ErrorCode.PARAMS_ERROR, "该邮箱已被绑定");
            }
            
            // 验证码验证：需验证新邮箱的验证码
            String key = String.format("email:code:%s", userAccount);
            String cacheCode = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.isBlank(cacheCode) || !cacheCode.equals(emailCode)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "验证码错误或已过期");
            }
             // 删除验证码
            stringRedisTemplate.delete(key);
            
            user.setUserAccount(userAccount);
        } else {
             throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数错误");
        }

        boolean result = userService.updateById(user);
        return ResultUtils.success(result);
    }

    // endregion

    // region 增删改查

    /**
     * 创建用户（仅管理员）
     *
     * @param userAddRequest 用户创建请求
     * @param request        HTTP请求
     * @return 新创建的用户ID
     */
    @PostMapping("/add")
    @Operation(summary = "创建用户",
            description = """
                    管理员创建新用户。

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest, HttpServletRequest request) {
        Long userId = userService.addUserByAdmin(userAddRequest);
        return ResultUtils.success(userId);
    }

    /**
     * 删除用户（仅管理员）
     *
     * @param deleteRequest 删除请求（包含用户ID）
     * @param request       HTTP请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @Operation(summary = "删除用户",
            description = """
                    管理员删除指定用户。

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户信息（仅管理员）
     *
     * @param userUpdateRequest 用户更新请求
     * @param request           HTTP请求
     * @return 是否更新成功
     */
    @PostMapping("/update")
    @Operation(summary = "更新用户",
            description = """
                    管理员更新用户信息。

                    **权限要求：**
                    - 仅限admin角色""")
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
     * 根据ID获取用户详情（仅管理员）
     *
     * @param id      用户ID
     * @param request HTTP请求
     * @return 用户实体类
     */
    @GetMapping("/get")
    @Operation(summary = "根据ID获取用户",
            description = """
                    管理员根据ID获取用户详细信息。

                    **权限要求：**
                    - 仅限admin角色""")
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
     * 根据ID获取用户封装类
     *
     * @param id      用户ID
     * @param request HTTP请求
     * @return 用户封装类（包含权限信息）
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据ID获取用户封装类",
            description = """
                    根据ID获取用户详情（封装类）。

                    **返回内容：**
                    - 用户基本信息
                    - 用户权限列表

                    **权限要求：**
                    - 需要登录""")
    public BaseResponse<UserVO> getUserVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        UserVO userVO = userService.getUserVO(user);
        
        if (userVO != null) {
            List<SysAuthority> authorities = sysAuthorityMapper.findByUserId(id);
            userVO.setAuthorities(authorities);
        }
        
        return ResultUtils.success(userVO);
    }

    /**
     * 分页获取用户列表（仅管理员）
     *
     * @param userQueryRequest 查询请求（分页参数）
     * @param request          HTTP请求
     * @return 用户分页列表
     */
    @PostMapping("/list/page")
    @Operation(summary = "分页获取用户列表",
            description = """
                    管理员分页查询用户列表。

                    **权限要求：**
                    - 仅限admin角色""")
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
     * @param userQueryRequest 查询请求（分页参数）
     * @param request          HTTP请求
     * @return 用户封装类分页列表
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页获取用户封装列表",
            description = """
                    分页查询用户列表（封装类）。

                    **权限要求：**
                    - 需要登录

                    **限制条件：**
                    - 每页最多20条""")
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
     * @param file    头像图片文件
     * @param request HTTP请求
     * @return 图片访问URL
     */
    @PostMapping("/upload/image")
    @Operation(summary = "上传头像图片",
            description = """
                    上传用户头像图片到对象存储。

                    **文件校验：**
                    - 最大5MB
                    - 仅支持图片格式

                    **权限要求：**
                    - 需要登录""")
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
     * @param userUpdateMyRequest 个人信息更新请求
     * @param request             HTTP请求
     * @return 是否更新成功
     */
    @PostMapping("/update/my")
    @Operation(summary = "更新个人信息",
            description = """
                    用户更新自己的个人信息。

                    **可修改字段：**
                    - 用户名
                    - 头像

                    **权限要求：**
                    - 需要登录
                    - 只能修改自己的信息""")
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
     * 查询所有角色及其对应的权限信息
     *
     * @return 角色权限列表
     */
    @Operation(summary = "查询所有角色及对应权限",
            description = """
                    获取系统中所有角色及其权限配置。

                    **返回内容：**
                    - 角色信息
                    - 角色对应的权限列表

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    @GetMapping("/getAuth")
    public BaseResponse< List<RoleWithAuthoritiesVO>> getAllRoleAndAuth(){
        List<RoleWithAuthoritiesVO> resList = userService.getRoleWithAuthoritiesVOS();
        return ResultUtils.success(resList);
    }

    /**
     * 修改用户角色
     *
     * @param userRoleUpdateRequest 用户角色更新请求
     * @param request               HTTP请求
     * @return 是否修改成功
     */
    @PostMapping("/update/roles")
    @Operation(summary = "修改用户角色",
            description = """
                    修改指定用户的角色。

                    **功能说明：**
                    - 一个用户可以拥有多个角色
                    - 角色变更后立即生效

                    **权限要求：**
                    - 仅限admin角色""")
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
     * @param roleAuthorityUpdateRequest 角色权限更新请求
     * @param request                    HTTP请求
     * @return 是否修改成功
     */
    @PostMapping("/role/update/authorities")
    @Operation(summary = "修改角色权限",
            description = """
                    修改指定角色的权限配置。

                    **功能说明：**
                    - 可以批量设置角色的权限
                    - 权限变更后立即生效

                    **权限要求：**
                    - 仅限admin角色""")
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
