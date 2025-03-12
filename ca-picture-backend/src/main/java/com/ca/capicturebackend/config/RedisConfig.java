package com.ca.capicturebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory lettuceConnectionFactory) {
        RedisTemplate<String, String> redisTemplate = new RedisTemplate<>();

        // 设置 String 类型的 key 序列化器
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        // 设置 String 类型的 value 序列化器
        redisTemplate.setValueSerializer(new StringRedisSerializer());

        // 设置 Hash 类型的 key 序列化器
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        // 设置 Hash 类型的 value 序列化器
        redisTemplate.setHashValueSerializer(new StringRedisSerializer());

        // 设置 Redis 连接 Lettuce 工厂
        redisTemplate.setConnectionFactory(lettuceConnectionFactory);

        // 确保所有属性设置完成后初始化
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }
}
