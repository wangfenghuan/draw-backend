package com.wfh.drawio.ai.config;

import com.wfh.drawio.ai.utils.DiagramContextUtil;
import io.micrometer.context.ContextRegistry;
import io.micrometer.context.ThreadLocalAccessor;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Hooks;

/**
 * @Title: ContextPropagationConfig
 * @Author wangfenghuan
 * @Package com.wfh.drawio.ai.config
 * @Date 2025/12/26 15:01
 * @description:
 */
@Configuration
public class ContextPropagationConfig {

    @PostConstruct
    public void init() {
        // 注册 Conversation ID 的传播器
        ContextRegistry.getInstance().registerThreadLocalAccessor(new ThreadLocalAccessor<String>() {
            // 在 Reactor Context 中的 Key
            @Override
            public Object key() {
                return "conversationId";
            }

            // 从当前线程获取值（用于抓取快照）
            @Override
            public String getValue() {
                return DiagramContextUtil.getConversationId();
            }

            // 将值设置到新线程（用于恢复现场）
            @Override
            public void setValue(String value) {
                DiagramContextUtil.bindConversationId(value);
            }

            // 清理新线程（用于执行完后还原）
            @Override
            public void reset() {
                // 注意：这里最好只清理 ID，不要调用 DiagramContextUtil.clear() 导致把 sink 也清了
                // 如果 DiagramContextUtil 没有单独清理 ID 的方法，暂时只能这样，或者你去 Util 加一个 removeId()
                DiagramContextUtil.bindConversationId(null);
            }
        });
        Hooks.enableAutomaticContextPropagation();
    }

}
