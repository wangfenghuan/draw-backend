package com.wfh.drawio.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.exception.ThrowUtils;
import com.wfh.drawio.model.dto.space.SpaceAddReqeust;
import com.wfh.drawio.model.dto.space.SpaceQueryRequest;
import com.wfh.drawio.model.entity.Diagram;
import com.wfh.drawio.model.entity.Space;
import com.wfh.drawio.model.entity.User;
import com.wfh.drawio.model.enums.SpaceLevelEnum;
import com.wfh.drawio.model.vo.SpaceVO;
import com.wfh.drawio.service.DiagramService;
import com.wfh.drawio.service.SpaceService;
import com.wfh.drawio.mapper.SpaceMapper;
import com.wfh.drawio.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jdk.jfr.Label;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.stream.Collectors;

/**
* @author fenghuanwang
* @description 针对表【space(空间)】的数据库操作Service实现
* @createDate 2026-01-05 11:05:00
*/
@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space>
    implements SpaceService{


    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private DiagramService diagramService;

    @Override
    public long addSpace(SpaceAddReqeust spaceAddReqeust, User loginUser){
        Space space = new Space();
        BeanUtils.copyProperties(spaceAddReqeust, space);
        // 默认值
        if (StrUtil.isBlank(spaceAddReqeust.getSpaceName())){
            space.setSpaceName("默认空间");
        }
        if (spaceAddReqeust.getSpaceLevel() == null){
            space.setSpaceLevel(SpaceLevelEnum.COMMON.getValue());
        }
        // 填充数据
        this.fillSpaceBySpaceLevel(space);
        Long id = loginUser.getId();
        space.setUserId(id);
        // 权限校验
        if (SpaceLevelEnum.COMMON.getValue() != spaceAddReqeust.getSpaceLevel() && !userService.isAdmin(loginUser)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限创建指定级别的团队空间");
        }
        // 针对用户进行加锁
        String lock = String.valueOf(id).intern();
        synchronized (lock){
            Long newSpaceId = transactionTemplate.execute(status -> {
                boolean exists = this.lambdaQuery().eq(Space::getUserId, id).exists();
                ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "每一个用户只能有一个私有的空间");
                // 写入数据库
                boolean save = this.save(space);
                ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR);
                return space.getId();
            });
            return Optional.ofNullable(newSpaceId).orElse(-1L);
        }
    }


    /**
     * 校验控件参数
     * @param space
     * @param add
     */
    @Override
    public void validSpace(Space space, boolean add) {
        ThrowUtils.throwIf(space == null, ErrorCode.PARAMS_ERROR);
        // 从对象中取值
        String spaceName = space.getSpaceName();
        Integer spaceLevel = space.getSpaceLevel();
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(spaceLevel);
        // 要创建
        if (add) {
            if (StrUtil.isBlank(spaceName)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称不能为空");
            }
            if (spaceLevel == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不能为空");
            }
        }
        // 修改数据时，如果要改空间级别
        if (spaceLevel != null && spaceLevelEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间级别不存在");
        }
        if (StrUtil.isNotBlank(spaceName) && spaceName.length() > 30) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间名称过长");
        }
    }

    /**
     * 填充空间限额
     * @param space
     */
    @Override
    public void fillSpaceBySpaceLevel(Space space) {
        // 根据空间级别，自动填充限额
        SpaceLevelEnum spaceLevelEnum = SpaceLevelEnum.getEnumByValue(space.getSpaceLevel());
        if (spaceLevelEnum != null) {
            long maxSize = spaceLevelEnum.getMaxSize();
            if (space.getMaxSize() == null) {
                space.setMaxSize(maxSize);
            }
            long maxCount = spaceLevelEnum.getMaxCount();
            if (space.getMaxCount() == null) {
                space.setMaxCount(maxCount);
            }
        }
    }

    /**
     * 检查权限
     * @param loginUser
     * @param diagram
     */
    @Override
    public void checkDiagramAuth(User loginUser, Diagram diagram) {
        Long spaceId = diagram.getSpaceId();
        if (spaceId == null) {
            // 公共图库，仅本人或管理员可操作
            if (!diagram.getUserId().equals(loginUser.getId()) && !userService.isAdmin(loginUser)) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        } else {
            // 私有空间，仅空间管理员可操作
            if (!diagram.getUserId().equals(loginUser.getId())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
            }
        }
    }

    /**
     * 删除空间并关联删除空间内的图表（带事务）
     */
    @Override
    public void deleteSpaceWithDiagrams(Long id) {
        transactionTemplate.execute(status -> {
            // 删除空间内的所有图表
            QueryWrapper<Diagram> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("spaceId", id);
            diagramService.remove(queryWrapper);
            // 即使没有关联图表，也不算失败

            // 删除空间
            boolean removeSpaceResult = this.removeById(id);
            ThrowUtils.throwIf(!removeSpaceResult, ErrorCode.OPERATION_ERROR);
            return true;
        });
    }

    /**
     * 获取查询条件
     *
     * @param spaceQueryRequest
     * @return
     */
    @Override
    public QueryWrapper<Space> getQueryWrapper(SpaceQueryRequest spaceQueryRequest) {
        QueryWrapper<Space> queryWrapper = new QueryWrapper<>();
        if (spaceQueryRequest == null) {
            return queryWrapper;
        }
        Long id = spaceQueryRequest.getId();
        Long userId = spaceQueryRequest.getUserId();
        String spaceName = spaceQueryRequest.getSpaceName();
        Integer spaceLevel = spaceQueryRequest.getSpaceLevel();
        String sortField = spaceQueryRequest.getSortField();
        String sortOrder = spaceQueryRequest.getSortOrder();

        // 精确查询
        queryWrapper.eq(ObjectUtils.isNotEmpty(id), "id", id);
        queryWrapper.eq(ObjectUtils.isNotEmpty(userId), "userId", userId);
        queryWrapper.eq(ObjectUtils.isNotEmpty(spaceLevel), "spaceLevel", spaceLevel);
        // 模糊查询
        queryWrapper.like(StrUtil.isNotBlank(spaceName), "spaceName", spaceName);
        // 排序
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField),
                sortOrder.equals("asc"), sortField);
        return queryWrapper;
    }

    /**
     * 获取空间封装
     *
     * @param space
     * @param request
     * @return
     */
    @Override
    public SpaceVO getSpaceVO(Space space, HttpServletRequest request) {
        // 对象转封装类
        SpaceVO spaceVO = SpaceVO.objToVo(space);
        // 可以在这里补充其他信息，比如用户信息等
        return spaceVO;
    }

    /**
     * 分页获取空间封装
     *
     * @param spacePage
     * @param request
     * @return
     */
    @Override
    public Page<SpaceVO> getSpaceVOPage(Page<Space> spacePage, HttpServletRequest request) {
        Page<SpaceVO> spaceVOPage = new Page<>(spacePage.getCurrent(), spacePage.getSize(), spacePage.getTotal());
        if (spacePage.getRecords() == null || spacePage.getRecords().isEmpty()) {
            return spaceVOPage;
        }
        // 对象列表 => 封装对象列表
        spaceVOPage.setRecords(spacePage.getRecords().stream()
                .map(space -> this.getSpaceVO(space, request))
                .collect(Collectors.toList()));
        return spaceVOPage;
    }

}




