package com.westsomsom.finalproject.reservation.application;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;

@RequiredArgsConstructor
public class ReservationPublisher {
    private final RedisTemplate<String, Object> redisTemplate;

    public void publishReservationEvent(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }
}
