package com.wfh.drawio.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.wfh.drawio.model.dto.spaceuser.SpaceUserAddRequest;
import com.wfh.drawio.model.dto.spaceuser.SpaceUserQueryRequest;
import com.wfh.drawio.model.entity.SpaceUser;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wfh.drawio.model.vo.SpaceUserVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author fenghuanwang
* @description 针对表【space_user(空间用户关联)】的数据库操作Service
* @createDate 2026-01-14 20:51:40
*/
public interface SpaceUserService extends IService<SpaceUser> {

    long addSpaceUser(SpaceUserAddRequest spaceUserAddRequest);

    void validSpaceUser(SpaceUser spaceUser, boolean add);

    QueryWrapper<SpaceUser> getQueryWrapper(SpaceUserQueryRequest spaceUserQueryRequest);

    SpaceUserVO getSpaceUserVO(SpaceUser spaceUser, HttpServletRequest request);

    List<SpaceUserVO> getSpaceUserVOList(List<SpaceUser> spaceUserList);
}
