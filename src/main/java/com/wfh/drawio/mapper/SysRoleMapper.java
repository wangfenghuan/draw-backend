package com.wfh.drawio.mapper;

import com.wfh.drawio.model.entity.SysRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wfh.drawio.model.vo.RoleAuthorityFlatVO;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author fenghuanwang
* @description 针对表【sys_role(系统角色表)】的数据库操作Mapper
* @createDate 2026-01-09 13:26:07
* @Entity com.wfh.drawio.model.entity.SysRole
*/
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询扁平化角色权限列表
     * @return
     */
    @Select("""
        SELECT
            r.id AS roleId,
            r.name AS roleName,
            r.description AS roleDescription,
            r.createTime AS roleCreateTime,
            r.updateTime AS roleUpdateTime,
            a.id AS authorityId,
            a.parentId,
            a.name AS authorityName,
            a.description AS authorityDescription,
            a.resource,
            a.type,
            a.createTime AS authorityCreateTime,
            a.updateTime AS authorityUpdateTime
        FROM sys_role r
        LEFT JOIN sys_role_authority_rel ra ON r.id = ra.roleId
        LEFT JOIN sys_authority a ON ra.authorityId = a.id AND a.isDelete = 0
        WHERE r.isDelete = 0
        ORDER BY r.id, a.id
        """)
    List<RoleAuthorityFlatVO> selectRoleWithAuthoritiesFlat();

}




