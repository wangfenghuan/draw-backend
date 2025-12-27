package com.wfh.drawio.ai.config;

import com.wfh.drawio.ai.model.StreamEvent;
import com.wfh.drawio.ai.utils.DiagramContextUtil;
import io.micrometer.context.ThreadLocalAccessor;
import reactor.core.publisher.Sinks;

/**
 * @Title: SinkAccessor
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.config
 * @Date 2025/12/27 10:21
 * @description:
 */
public class SinkAccessor implements ThreadLocalAccessor<Sinks.Many<StreamEvent>> {
    @Override
    public Object key() {
        return DiagramContextUtil.KEY_SINK;
    }

    @Override
    public Sinks.Many<StreamEvent> getValue() {
        return DiagramContextUtil.getSink();
    }

    @Override
    public void setValue(Sinks.Many<StreamEvent> streamEventMany) {
        DiagramContextUtil.bindSink(streamEventMany);
    }

    @Override
    public void reset() {
        DiagramContextUtil.EVENT_SINK.remove();
    }
}
