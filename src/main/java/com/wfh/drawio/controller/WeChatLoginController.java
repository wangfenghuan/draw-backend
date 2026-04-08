package com.wfh.drawio.controller;

import com.wfh.drawio.annotation.RateLimit;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.model.dto.wechat.WeChatScanLoginRequest;
import com.wfh.drawio.model.enums.RateLimitType;
import com.wfh.drawio.model.vo.wechat.WeChatLoginStatusVO;
import com.wfh.drawio.model.vo.wechat.WeChatQrCodeVO;
import com.wfh.drawio.service.WeChatLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 微信扫码登录控制器
 *
 * @author fenghuanwang
 */
@RestController
@RequestMapping("/wechat/login")
@Slf4j
@Tag(name = "weChatLoginController", description = "微信小程序扫码登录相关接口")
public class WeChatLoginController {

    @Resource
    private WeChatLoginService weChatLoginService;

    /**
     * 生成扫码登录二维码
     * PC 端调用此接口获取小程序码图片，展示给用户扫描
     *
     * @return 二维码信息（包含 sceneId、图片Base64、过期时间）
     */
    @GetMapping("/qrcode")
    @Operation(summary = "生成扫码登录二维码", description = "PC端调用，返回小程序码图片和场景ID")
    @RateLimit(limitType = RateLimitType.IP, rate = 10, rateInterval = 60, message = "获取二维码过于频繁")
    public BaseResponse<WeChatQrCodeVO> generateQrCode() {
        WeChatQrCodeVO qrCodeVO = weChatLoginService.generateQrCode();
        return ResultUtils.success(qrCodeVO);
    }

    /**
     * 查询登录状态
     * PC 端轮询此接口，检查用户是否已完成扫码登录
     *
     * @param sceneId 场景ID（从生成二维码接口获取）
     * @return 登录状态（waiting/scanned/success/expired）
     */
    @GetMapping("/status")
    @Operation(summary = "查询登录状态", description = "PC端轮询，返回当前扫码状态和登录凭证")
    @Parameter(name = "sceneId", description = "场景ID", required = true, example = "abc123def456")
    public BaseResponse<WeChatLoginStatusVO> queryStatus(@RequestParam String sceneId) {
        WeChatLoginStatusVO statusVO = weChatLoginService.queryLoginStatus(sceneId);
        return ResultUtils.success(statusVO);
    }

    /**
     * 小程序扫码确认登录
     * 小程序端调用此接口，传递 sceneId 和 wx.login 获取的 code
     *
     * @param request 扫码请求（包含 sceneId、code、可选的用户昵称和头像）
     * @return 是否成功
     */
    @PostMapping("/scan")
    @Operation(summary = "小程序扫码确认登录", description = "小程序端调用，传递场景ID和微信code完成登录")
    @RateLimit(limitType = RateLimitType.IP, rate = 20, rateInterval = 60, message = "扫码请求过于频繁")
    public BaseResponse<Boolean> scanLogin(@RequestBody WeChatScanLoginRequest request) {
        weChatLoginService.scanLogin(request);
        return ResultUtils.success(true);
    }
}