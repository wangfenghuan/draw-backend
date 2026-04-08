package com.wfh.drawio.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.model.entity.Conversion;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.service.ConversionService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * @Title: ConversionController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2025/12/23 16:48
 * @description:
 */
@RestController
@RequestMapping("/conversion")
public class ConversionController {


    @Resource
    private UserService userService;

    @Resource
    private ConversionService conversionService;

    /**
     * 分页查询某个图表的对话历史
     *
     * @param diagramId      图表ID
     * @param pageSize       每页数量（默认10条）
     * @param lasteCreateTime 上次查询的创建时间（用于游标分页）
     * @param request        HTTP请求
     * @return 对话历史分页列表
     */
    @GetMapping("/diagram/{diagramId}")
    @Operation(summary = "分页查询图表对话历史",
            description = """
                    分页查询指定图表的AI对话历史记录。

                    **功能说明：**
                    - 查询图表的AI生成对话历史
                    - 支持游标分页（基于创建时间）
                    - 返回对话记录列表

                    **权限要求：**
                    - 需要登录
                    - 仅图表创建人可查询""")
    public BaseResponse<Page<Conversion>> listDiagramChatHistory(
            @PathVariable Long diagramId,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false)LocalDateTime lasteCreateTime,
            HttpServletRequest request
            ){
        User loginUser = userService.getLoginUser(request);
        Page<Conversion> conversionPage = conversionService.listDiagramChatHistoryByPage(diagramId, pageSize, lasteCreateTime, loginUser);
        return ResultUtils.success(conversionPage);
    }

}
