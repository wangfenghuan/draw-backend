package com.wfh.drawio.model.dto.codeparse;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Title: PackageInfoDTO
 * @Author wangfenghuan
 * @Package com.wfh.drawio.model.dto.codeparse
 * @Date 2026/2/16
 * @description: Package information DTO
 */
@Data
public class PackageInfoDTO implements Serializable {
    /**
     * Package name
     */
    private String packageName;
    
    /**
     * Classes in this package
     */
    private List<ClassInfoDTO> classes;
    
    /**
     * Dependencies (imports) in this package
     */
    private List<DependencyInfoDTO> dependencies;
}
