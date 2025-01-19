package com.westsomsom.finalproject.chat.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;

@RequiredArgsConstructor
public class ChatMessagePublisher {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic channelTopic;

    public void publish(String message) {
        redisTemplate.convertAndSend(channelTopic.getTopic(), message);
    }
}
