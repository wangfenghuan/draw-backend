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
import com.wfh.drawio.model.vo.MaterialVO;
import com.wfh.drawio.service.MaterialService;
import com.wfh.drawio.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
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
 * @description: 素材管理接口（仅管理员）
 */
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
     * @param materialAddRequest
     * @param request
     * @return
     */
    @PostMapping("/add")
    @Operation(summary = "创建素材")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<Long> addMaterial(@RequestBody MaterialAddRequest materialAddRequest,
                                          HttpServletRequest request) {
        if (materialAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Material material = new Material();
        BeanUtils.copyProperties(materialAddRequest, material);

        // 从请求中获取当前登录用户ID
        com.wfh.drawio.model.entity.User loginUser = userService.getLoginUser(request);
        material.setUserId(loginUser.getId());

        boolean result = materialService.save(material);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(material.getId());
    }

    /**
     * 删除素材
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @Operation(summary = "删除素材")
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
     * @param materialUpdateRequest
     * @param request
     * @return
     */
    @PostMapping("/update")
    @Operation(summary = "更新素材")
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
     * 根据 id 获取素材
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get")
    @Operation(summary = "根据 id 获取素材")
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
     * 根据 id 获取素材封装类
     *
     * @param id
     * @param request
     * @return
     */
    @GetMapping("/get/vo")
    @Operation(summary = "根据 id 获取素材封装类")
    @PreAuthorize("hasAuthority('admin')")
    public BaseResponse<MaterialVO> getMaterialVOById(long id, HttpServletRequest request) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Material material = materialService.getById(id);
        ThrowUtils.throwIf(material == null, ErrorCode.NOT_FOUND_ERROR);
        MaterialVO materialVO = materialService.getMaterialVO(material);
        return ResultUtils.success(materialVO);
    }

    /**
     * 分页获取素材列表
     *
     * @param materialQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page")
    @Operation(summary = "分页获取素材列表")
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
     * @param materialQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list/page/vo")
    @Operation(summary = "分页获取素材封装列表")
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

