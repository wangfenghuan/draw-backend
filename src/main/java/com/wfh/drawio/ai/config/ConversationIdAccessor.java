package com.wfh.drawio.ai.config;

import com.wfh.drawio.ai.utils.DiagramContextUtil;
import io.micrometer.context.ThreadLocalAccessor;

/**
 * @author fenghuanwang
 */
public class ConversationIdAccessor implements ThreadLocalAccessor<String> {

    @Override
    public Object key() {
        return DiagramContextUtil.KEY_CONVERSATION_ID;
    }

    @Override
    public String getValue() {
        return DiagramContextUtil.getConversationId();
    }

    @Override
    public void setValue(String value) {
        DiagramContextUtil.bindConversationId(value);
    }

    @Override
    public void reset() {
        DiagramContextUtil.bindConversationId(null);
    }
}