package com.westsomsom.finalproject.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Arrays;

@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.cluster.nodes}")
    private String redisClusterNodes; // 클러스터 노드들 (쉼표로 구분된 리스트)

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // Redis 클러스터 구성 설정
        RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(Arrays.asList(redisClusterNodes.split(",")));

        // TLS(SSL) 활성화 설정 추가
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .useSsl()  // 🔥 TLS(SSL) 활성화
                .build();

        return new LettuceConnectionFactory(clusterConfiguration, clientConfig);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
