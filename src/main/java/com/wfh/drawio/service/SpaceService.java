package com.wfh.drawio.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wfh.drawio.model.dto.space.SpaceAddReqeust;
import com.wfh.drawio.model.dto.space.SpaceQueryRequest;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.vo.SpaceVO;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author fenghuanwang
* @description 针对表【space(空间)】的数据库操作Service
* @createDate 2026-01-05 11:05:00
*/
public interface SpaceService extends IService<Space> {

    /**
     * 添加控件
     * @param spaceAddReqeust
     * @param loginUser
     * @return
     */
    long addSpace(SpaceAddReqeust spaceAddReqeust, User loginUser);

    /**
     * 校验空间
     * @param space
     * @param add
     */
    void validSpace(Space space, boolean add);

    /**
     * 填充控件限额
     * @param space
     */
    void fillSpaceBySpaceLevel(Space space);

    void checkDiagramAuth(User loginUser, Diagram diagram);

    /**
     * 删除空间并关联删除空间内的图表（带事务）
     *
     * @param id 空间ID
     */
    void deleteSpaceWithDiagrams(Long id);

    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest
     * @return
     */
    QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest);

    /**
     * 获取空间封装
     *
     * @param space
     * @param request
     * @return
     */
    SpaceVO getSpaceVO(Space space, HttpServletRequest request);

    /**
     * 分页获取空间封装
     *
     * @param spacePage
     * @param request
     * @return
     */
    Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request);
}
