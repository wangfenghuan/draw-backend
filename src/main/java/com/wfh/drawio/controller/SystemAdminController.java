package com.wfh.drawio.controller;

import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ResultUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import static com.wfh.drawio.constant.RedisPrefixConstant.*;

/**
 * @Title: SystemAdminController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2025/12/28 11:05
 * @description: 系统管理接口
 */
@Tag(name = "systemAdminController", description = "系统配置和AI服务管理接口")
@RestController
@RequestMapping("/admin/system")
public class SystemAdminController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 全局禁用AI服务
     *
     * @return 操作结果提示
     */
    @PostMapping("/shutdown-ai")
    @Operation(summary = "全局禁用AI服务",
            description = """
                    禁用系统中所有用户的AI服务。

                    **功能说明：**
                    - 设置Redis全局开关为false
                    - 立即生效，所有AI请求将被拒绝

                    **权限要求：**
                    - 仅限admin角色""")
    public BaseResponse<String> shutdownAi() {
        // 设置为 false，立即生效
        stringRedisTemplate.opsForValue().set(GLOBAL_AI_SWITCH_KEY, "false");
        return ResultUtils.success("AI 服务已全局禁用");
    }

    /**
     * 全局启用AI服务
     *
     * @return 操作结果提示
     */
    @PostMapping("/resume-ai")
    @Operation(summary = "全局启用AI服务",
            description = """
                    启用系统中所有用户的AI服务。

                    **功能说明：**
                    - 设置Redis全局开关为true
                    - 立即生效，恢复AI服务

                    **权限要求：**
                    - 仅限admin角色""")
    public BaseResponse<String> resumeAi() {
        stringRedisTemplate.opsForValue().set(GLOBAL_AI_SWITCH_KEY, "true");
        return ResultUtils.success("AI 服务已全局启用");
    }

    /**
     * 获取全局AI服务状态
     *
     * @return AI服务状态（true=启用，false=禁用）
     */
    @GetMapping("/status-ai")
    @Operation(summary = "获取全局AI服务状态",
            description = """
                    查询系统AI服务的全局开关状态。

                    **返回值：**
                    - true：AI服务已启用
                    - false：AI服务已禁用
                    - 默认为启用状态

                    **权限要求：**
                    - 仅限admin角色""")
    public BaseResponse<Boolean> getGlobalAiStatus() {
        String status = stringRedisTemplate.opsForValue().get(GLOBAL_AI_SWITCH_KEY);
        // 默认开启
        boolean isEnabled = !"false".equalsIgnoreCase(status);
        return ResultUtils.success(isEnabled);
    }

    /**
     * 切换指定用户的AI服务权限
     *
     * @param userId 用户ID
     * @param enable true=启用，false=禁用
     * @return 操作结果提示
     */
    @PostMapping("/user-ai-switch")
    @Operation(summary = "切换用户AI服务权限",
            description = """
                    单独控制指定用户的AI服务权限。

                    **功能说明：**
                    - 设置用户级别的AI开关
                    - 不影响全局开关，可精细控制

                    **权限要求：**
                    - 仅限admin角色""")
    public BaseResponse<String> toggleUserAi(@RequestParam Long userId, @RequestParam Boolean enable) {
        stringRedisTemplate.opsForValue().set(USER_AI_SWITCH_KEY + userId, enable.toString());
        String message = String.format("用户 %d 的AI服务已%s", userId, enable ? "启用" : "禁用");
        return ResultUtils.success(message);
    }

    /**
     * 获取指定用户的AI服务状态
     *
     * @param userId 用户ID
     * @return AI服务状态（true=启用，false=禁用）
     */
    @GetMapping("/user-ai-status")
    @Operation(summary = "获取用户AI服务状态",
            description = """
                    查询指定用户的AI服务开关状态。

                    **返回值：**
                    - true：该用户AI服务已启用
                    - false：该用户AI服务已禁用
                    - 默认为启用状态

                    **权限要求：**
                    - 仅限admin角色""")
    public BaseResponse<Boolean> getUserAiStatus(@RequestParam Long userId) {
        String status = stringRedisTemplate.opsForValue().get(USER_AI_SWITCH_KEY + userId);
        // 默认开启
        boolean isEnabled = !"false".equalsIgnoreCase(status);
        return ResultUtils.success(isEnabled);
    }

    /**
     * 获取当前AI使用量统计
     *
     * @return AI使用量数据（如总耗时或调用次数）
     */
    @GetMapping("/ai-usage")
    @Operation(summary = "获取AI使用量统计",
            description = """
                    查询系统AI服务的使用量统计。

                    **返回内容：**
                    - AI调用总量统计

                    **权限要求：**
                    - 仅限admin角色""")
    public BaseResponse<String> getAiUsage() {
        String usage = stringRedisTemplate.opsForValue().get(GLOBAL_AI_TOKEN_KEY);
        return ResultUtils.success(usage != null ? usage : "0");
    }
}