package com.wfh.drawio.model.vo;

import com.wfh.drawio.model.entity.Feedback;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;

/**
 * 用户反馈视图
 *
 * @author fenghuanwang
 */
@Data
@Schema(name = "FeedbackVO", description = "用户反馈视图对象")
public class FeedbackVO implements Serializable {

    /**
     * id
     */
    @Schema(description = "反馈ID", example = "123456789")
    private Long id;

    /**
     * 反馈内容
     */
    @Schema(description = "反馈内容", example = "在使用过程中发现了一个bug...")
    private String content;

    /**
     * 反馈图片URL
     */
    @Schema(description = "反馈图片URL", example = "https://example.com/feedback.jpg")
    private String pictureUrl;

    /**
     * 反馈用户 id
     */
    @Schema(description = "反馈用户ID", example = "10001")
    private Long userId;

    /**
     * 反馈用户信息
     */
    @Schema(description = "反馈用户信息")
    private UserVO userVO;

    /**
     * 创建时间
     */
    @Schema(description = "创建时间", example = "2024-01-01 10:00:00")
    private Date createTime;

    /**
     * 更新时间
     */
    @Schema(description = "更新时间", example = "2024-01-01 10:00:00")
    private Date updateTime;

    /**
     * 封装类转对象
     *
     * @param feedbackVO
     * @return
     */
    public static Feedback voToObj(FeedbackVO feedbackVO) {
        if (feedbackVO == null) {
            return null;
        }
        Feedback feedback = new Feedback();
        BeanUtils.copyProperties(feedbackVO, feedback);
        return feedback;
    }

    /**
     * 对象转封装类
     *
     * @param feedback
     * @return
     */
    public static FeedbackVO objToVo(Feedback feedback) {
        if (feedback == null) {
            return null;
        }
        FeedbackVO feedbackVO = new FeedbackVO();
        BeanUtils.copyProperties(feedback, feedbackVO);
        return feedbackVO;
    }
}
