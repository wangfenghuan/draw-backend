package com.wfh.drawio.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wfh.drawio.common.ErrorCode;
import com.wfh.drawio.exception.BusinessException;
import com.wfh.drawio.mapper.FeedbackMapper;
import com.wfh.drawio.model.dto.feedback.FeedbackQueryRequest;
import com.wfh.drawio.model.entity.Feedback;
import com.wfh.drawio.model.vo.FeedbackVO;
import com.wfh.drawio.service.FeedbackService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
* @author fenghuanwang
* @description 针对表【feedback(用户反馈表)】的数据库操作Service实现
* @createDate 2026-01-24 20:17:09
*/
@Service
public class FeedbackServiceImpl extends ServiceImpl<FeedbackMapper, Feedback>
    implements FeedbackService{

    @Override
    public QueryWrapper<Feedback> getQueryWrapper(FeedbackQueryRequest feedbackQueryRequest) {
        if (feedbackQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = feedbackQueryRequest.getId();
        String content = feedbackQueryRequest.getContent();
        Long userId = feedbackQueryRequest.getUserId();
        String sortField = feedbackQueryRequest.getSortField();
        String sortOrder = feedbackQueryRequest.getSortOrder();

        QueryWrapper<Feedback> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.like(StringUtils.isNotBlank(content), "content", content);
        queryWrapper.orderBy(StringUtils.isNotBlank(sortField),
                "desc".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public FeedbackVO getFeedbackVO(Feedback feedback) {
        if (feedback == null) {
            return null;
        }
        FeedbackVO feedbackVO = FeedbackVO.objToVo(feedback);
        return feedbackVO;
    }

    @Override
    public List<FeedbackVO> getFeedbackVO(List<Feedback> feedbackList) {
        if (CollUtil.isEmpty(feedbackList)) {
            return List.of();
        }
        return feedbackList.stream().map(this::getFeedbackVO).collect(Collectors.toList());
    }
}




