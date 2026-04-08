package com.wfh.drawio.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wfh.drawio.common.BaseResponse;
import com.wfh.drawio.common.DeleteRequest;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.common.ResultUtils;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.material.MaterialAddRequest;
import com.wfh.drawio.model.dto.material.MaterialQueryRequest;
import com.wfh.drawio.model.dto.material.MaterialUpdateRequest;
import com.wfh.drawio.model.entity.Material;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.vo.MaterialVO;
import com.wfh.drawio.model.vo.UserVO;
import com.wfh.drawio.service.MaterialService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Title: MaterialController
 * @Author wangfenghuan
 * @Package com.wfh.drawio.controller
 * @Date 2026/1/24 14:42
 * @description: 素材管理接口
 */
@Tag(name = "素材管理", description = "素材资源的增删改查接口（仅管理员）")
@RestController
@RequestMapping("/material")
@Slf4j
public class MaterialController {

    @Resource
    private MaterialService materialService;

    @Resource
    private UserService userService;

    /**
     * 创建素材
     *
     * @param materialAddRequest 素材创建请求
     * @param request            HTTP请求
     * @return 新创建的素材ID
     */
    @PostMapping("/add")
    @Operation(summary = "创建素材",
            description = """
                    创建新的素材资源。

                    **功能说明：**
                    - 创建素材记录
                    - 自动关联当前登录用户

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Long> addMaterial(@RequestBody MaterialAddRequest materialAddRequest,
                                          HttpServletRequest request) {
        if (materialAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Material material = new Material();
        BeanUtils.copyProperties(materialAddRequest, material);

        // 从请求中获取当前登录用户ID
        User loginUser = userService.getLoginUser(request);
        material.setUserId(loginUser.getId());
        boolean result = materialService.save(material);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(material.getId());
    }

    /**
     * 删除素材
     *
     * @param deleteRequest 删除请求（包含素材ID）
     * @param request       HTTP请求
     * @return 是否删除成功
     */
    @PostMapping("/delete")
    @Operation(summary = "删除素材",
            description = """
                    删除指定的素材资源。

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> deleteMaterial(@RequestBody DeleteRequest deleteRequest,
                                                HttpServletRequest request) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = materialService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新素材
     *
     * @param materialUpdateRequest 素材更新请求
     * @param request               HTTP请求
     * @return 是否更新成功
     */
    @PostMapping("/update")
    @Operation(summary = "更新素材",
            description = """
                    更新素材资源信息。

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Boolean> updateMaterial(@RequestBody MaterialUpdateRequest materialUpdateRequest,
                                                HttpServletRequest request) {
        if (materialUpdateRequest == null || materialUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Material material = new Material();
        BeanUtils.copyProperties(materialUpdateRequest, material);
        boolean result = materialService.updateById(material);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据ID获取素材详情
     *
     * @param id      素材ID
     * @param request HTTP请求
     * @return 素材实体类
     */
    @GetMapping("/get")
    @Operation(summary = "根据ID获取素材",
            description = """
                    根据ID获取素材详细信息（实体类）。

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Material> getMaterialById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Material material = materialService.getById(id);
        ThrowUtils.throwIf(material == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(material);
    }

    /**
     * 根据ID获取素材封装类
     *
     * @param id      素材ID
     * @param request HTTP请求
     * @return 素材封装类（包含创建用户信息）
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据ID获取素材封装类",
            description = """
                    根据ID获取素材详情（封装类）。

                    **返回内容：**
                    - 素材基本信息
                    - 创建用户信息

                    **权限要求：**
                    - 需要登录""")
    public BaseResponse<MaterialVO> getMaterialVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Material material = materialService.getById(id);
        ThrowUtils.throwIf(material == null, ErrorCode.NOT_FOUND_ERROR);
        User loginUser = userService.getById(material.getUserId());
        UserVO userVO = userService.getUserVO(loginUser);
        MaterialVO materialVO = materialService.getMaterialVO(material);
        materialVO.setUserVO(userVO);
        return ResultUtils.success(materialVO);
    }

    /**
     * 分页获取素材列表（仅管理员）
     *
     * @param materialQueryRequest 查询请求（分页参数）
     * @param request               HTTP请求
     * @return 素材分页列表
     */
    @PostMapping("/list/page")
    @Operation(summary = "分页获取素材列表",
            description = """
                    分页查询素材列表（实体类）。

                    **权限要求：**
                    - 仅限admin角色""")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Page<Material>> listMaterialByPage(@RequestBody MaterialQueryRequest materialQueryRequest,
                                                            HttpServletRequest request) {
        if (materialQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = materialQueryRequest.getCurrent();
        long size = materialQueryRequest.getPageSize();
        Page<Material> materialPage = materialService.page(new Page<>(current, size),
                materialService.getQueryWrapper(materialQueryRequest));
        return ResultUtils.success(materialPage);
    }

    /**
     * 分页获取素材封装列表
     *
     * @param materialQueryRequest 查询请求（分页参数）
     * @param request               HTTP请求
     * @return 素材封装类分页列表
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页获取素材封装列表",
            description = """
                    分页查询素材列表（封装类）。

                    **返回内容：**
                    - 素材基本信息

                    **权限要求：**
                    - 需要登录

                    **限制条件：**
                    - 每页最多20条""")
    public BaseResponse<Page<MaterialVO>> listMaterialVOByPage(@RequestBody MaterialQueryRequest materialQueryRequest,
                                                                HttpServletRequest request) {
        if (materialQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = materialQueryRequest.getCurrent();
        long size = materialQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<Material> materialPage = materialService.page(new Page<>(current, size),
                materialService.getQueryWrapper(materialQueryRequest));
        Page<MaterialVO> materialVOPage = new Page<>(current, size, materialPage.getTotal());
        List<MaterialVO> materialVOList = materialService.getMaterialVO(materialPage.getRecords());
        materialVOPage.setRecords(materialVOList);
        return ResultUtils.success(materialVOPage);
    }
}

