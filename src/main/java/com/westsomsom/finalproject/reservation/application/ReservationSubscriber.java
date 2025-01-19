package com.westsomsom.finalproject.reservation.application;

import com.westsomsom.finalproject.reservation.dao.ReservationRepository;
import com.westsomsom.finalproject.reservation.domain.Reservation;
import com.westsomsom.finalproject.reservation.domain.ReservationStatus;
import com.westsomsom.finalproject.store.application.StoreService;
import com.westsomsom.finalproject.store.domain.Store;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationSubscriber implements MessageListener {
    private final ReservationRepository reservationRepository;
    private final StoreService storeService;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(pattern);
        String receivedMessage = new String(message.getBody());
        log.info("Received message: {} from channel: {}", receivedMessage,channel);

        // 예약 처리 로직 실행
        processReservation(receivedMessage);
    }

    private void processReservation(String message) {
        int maxAttempts = 5;
        int retryDelay = 100; // 시작 딜레이 (ms)

        LOOP: for (int attempts = 1; attempts <= maxAttempts; attempts++) {
            try {
                String[] parts = message.split("\\|");
                int storeId = Integer.parseInt(parts[0].replaceAll("[^0-9]", "").trim());
                String date = parts[1];
                String timeSlot = parts[2];
                String parts3 = parts[3];
                String userId = parts3.substring(0,parts[3].length()-1);

                String slotKey = "availableSlots|" + storeId + "|" + date + "|" + timeSlot;
                String queueKey = "reservationQueue|" + storeId + "|" + date + "|" + timeSlot;

                // 예약 가능한 슬롯 수 확인
                //String slotValue = (String) redisTemplate.opsForValue().get(slotKey);
                //int availableSlots = slotValue != null ? Integer.parseInt(slotValue) : 0;

                List<Object> queue = redisTemplate.opsForList().range(queueKey, 0, -1);
                if (queue != null && queue.contains(userId)) {
                    Store store = storeService.findById(storeId)
                            .orElseThrow(() -> new RuntimeException("Store not found for ID: " + storeId));

                    Long queueRemovedCount = redisTemplate.opsForList().remove(queueKey, 0, userId);
                    if (queueRemovedCount > 0) {
                        log.info("✅ [대기열 취소] 사용자 '{}'가 Redis List에서 제거됨.", userId);
                    } else {
                        log.warn("🚨 [대기열 취소] 사용자 '{}' 제거 실패! queueKey: {}", userId, queueKey);
                        break LOOP;
                    }

                    Reservation reservation = reservationRepository.save(Reservation.builder()
                            .store(store)
                            .date(date)
                            .timeSlot(timeSlot)
                            .user(userId)
                            .status(ReservationStatus.COMPLETED)
                            .build());

                    log.info("✅ 예약 완료: 사용자 {}", userId);
                    redisTemplate.opsForValue().decrement(slotKey);
                    log.info("Updated available slots: {} for {}", redisTemplate.opsForValue().get(slotKey), slotKey);

                    break LOOP;
                }else{
                    log.info("이미 예약한 사용자입니다.");
                    break LOOP;
                }
            } catch (Exception e) {
                log.error("🚨 예약 처리 실패. 재시도 시도: {}/{}", attempts, maxAttempts, e);

                if (attempts == maxAttempts) {
                    log.error("❌ 최대 재시도 횟수 초과. 예약 처리 중단: {}", message);
                    break LOOP;
                }

                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("⏳ 재시도 중 인터럽트 발생.", ie);
                    break LOOP;
                }

                retryDelay *= 2;
            }
        }
    }
}