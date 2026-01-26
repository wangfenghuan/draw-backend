package com.wfh.drawio.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;
import software.amazon.awssdk.services.s3.endpoints.internal.Value;

/**
 * @Title: RedisConfig
 * @Author wangfenghuan
 * @Package com.wfh.drawio.config
 * @Date 2026/1/22 19:36
 * @description:
 */
@Configuration
public class RedisConfig {

    /**
     * key是房间号，值是yjs二进制数据
     * @param connectionFactory
     * @return
     */
    @Bean
    public RedisTemplate<String, byte[]> bytesRedisTemplate(RedisConnectionFactory connectionFactory){
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        return template;
    }
}
