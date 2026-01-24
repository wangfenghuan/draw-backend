package com.wfh.drawio.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.wfh.drawio.model.dto.feedback.FeedbackQueryRequest;
import com.wfh.drawio.model.entity.Feedback;
import com.wfh.drawio.model.vo.FeedbackVO;

import java.util.List;

/**
* @author fenghuanwang
* @description 针对表【feedback(用户反馈表)】的数据库操作Service
* @createDate 2026-01-24 20:17:09
*/
public interface FeedbackService extends IService<Feedback> {

    /**
     * 获取查询条件
     *
     * @param feedbackQueryRequest
     * @return
     */
    QueryWrapper<Feedback> getQueryWrapper(FeedbackQueryRequest feedbackQueryRequest);

    /**
     * 获取反馈VO封装
     *
     * @param feedback
     * @return
     */
    FeedbackVO getFeedbackVO(Feedback feedback);

    /**
     * 获取反馈VO封装列表
     *
     * @param feedbackList
     * @return
     */
    List<FeedbackVO> getFeedbackVO(List<Feedback> feedbackList);
}
