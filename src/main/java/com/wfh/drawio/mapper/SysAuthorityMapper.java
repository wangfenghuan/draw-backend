package com.wfh.drawio.mapper;

import com.wfh.drawio.model.entity.SysAuthority;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author fenghuanwang
* @description 针对表【sys_authority(系统权限资源表)】的数据库操作Mapper
* @createDate 2026-01-09 13:26:07
* @Entity com.wfh.drawio.model.entity.SysAuthority
*/
public interface SysAuthorityMapper extends BaseMapper<SysAuthority> {


    /**
     * 根据用户ID查询所有权限
     * 路径：User -> sys_user_role_rel -> sys_role_authority_rel -> sys_authority
     */
    @ResultMap("BaseResultMap")
    @Select("SELECT DISTINCT a.* " +
            "FROM sys_authority a " +
            "INNER JOIN sys_role_authority_rel ra ON a.id = ra.authorityId " +
            "INNER JOIN sys_user_role_rel ur ON ra.roleId = ur.roleId " +
            "WHERE ur.userId = #{userId} " +
            "AND a.isDelete = 0 " +
            "AND ra.isDelete = 0 " +
            "AND ur.isDelete = 0")
    List<SysAuthority> findByUserId(Long userId);

}




