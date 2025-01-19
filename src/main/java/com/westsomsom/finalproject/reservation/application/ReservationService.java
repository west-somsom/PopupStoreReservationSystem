package com.westsomsom.finalproject.reservation.application;

import com.westsomsom.finalproject.notification.application.NotificationService;
import com.westsomsom.finalproject.reservation.dao.ReservationRepository;
import com.westsomsom.finalproject.reservation.domain.Reservation;
import com.westsomsom.finalproject.reservation.domain.ReservationStatus;
import com.westsomsom.finalproject.store.application.StoreService;
import com.westsomsom.finalproject.store.domain.Store;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {
    private final RedisTemplate<String, Object> redisTemplate;
    private final ReservationRepository reservationRepository;
    private final StoreService storeService;
    private final NotificationService notificationService;
    private final ReservationPublisher reservationPublisher;

    private static final String REDIS_QUEUE_KEY = "reservationQueue|";
    private static final String UNIQUE_USERS_KEY = "uniqueUsers|";
    private static final String AVAILABLE_SLOTS_KEY = "availableSlots|";

    //예약 요청이 들어오면 예약 가능 시간인지 확인
    public boolean checkReservationTime(int storeId) {
        LocalDateTime now = LocalDateTime.now();
        Store store = storeService.findById(storeId)
                .orElseThrow(() -> new RuntimeException("Store not found for ID: " + storeId));
        //해당 팝업 예약 오픈 시간과 비교하게 수정
        if (now.isBefore(store.getReservationStart()) || now.isAfter(store.getReservationFin())) {
            return false;
        }
        return true;
    }

    //몇번째인지 확인
    public String joinQueue(String date, String timeSlot, String memberId, int storeId) {
        String res="";
        String queueKey = REDIS_QUEUE_KEY + storeId + "|" + date + "|" + timeSlot;
        String uniqueUsersKey = UNIQUE_USERS_KEY + storeId + "|" + date + "|" + timeSlot;
        String slotKey = AVAILABLE_SLOTS_KEY + storeId + "|" + date + "|" + timeSlot;

        // Redis Set에서 사용자 중복 확인
        Boolean isAlreadyReserved = redisTemplate.opsForHash().hasKey(uniqueUsersKey, memberId);
        if (Boolean.TRUE.equals(isAlreadyReserved)) {
            res = "사용자가 이미 예약 요청을 보냈습니다: "+memberId;
            log.info("사용자가 이미 예약 요청을 보냈습니다: {}", memberId);
            return res;
        }

        // 대기열 중복 확인
        List<Object> queue = redisTemplate.opsForList().range(queueKey, 0, -1);
        if (queue != null && queue.contains(memberId)) {
            res = "사용자가 이미 대기열에 있습니다: "+ memberId;
            log.info("사용자가 이미 대기열에 있습니다: {}", memberId);
            return res;
        }

        // 대기열 크기 제한 확인
        Long queueSize = redisTemplate.opsForList().size(queueKey);
        int maxQueueSize = 10000; // 대기열 최대 크기 500->10000
        if (queueSize != null && queueSize >= maxQueueSize) {
            res = "대기열이 가득 찼습니다.";
            log.info("대기열이 가득 찼습니다.");
            return res;
        }

        String slotValue = (String) redisTemplate.opsForValue().get(slotKey);
        int availableSlots = slotValue != null ? Integer.parseInt(slotValue) : 0;
        log.info("🔍 [예약 가능 슬롯 확인] 현재 슬롯 수: {}", availableSlots);

        if (availableSlots > 0) {
            redisTemplate.opsForHash().put(uniqueUsersKey, memberId, "1");
            log.info("✅ [Redis] 사용자 '{}' 추가 완료! uniqueUsersKey: {}", memberId, uniqueUsersKey);
            res+="예약";
        } else {
            log.info("🚨 [예약 불가] 예약이 마감되었습니다.");
            Long queueRemovedCount = redisTemplate.opsForList().remove(queueKey, 0, memberId);
            if (queueRemovedCount > 0) {
                log.info("✅ [대기열 취소] 사용자 '{}'가 Redis List에서 제거됨.", memberId);
            } else {
                log.warn("🚨 [대기열 취소] 사용자 '{}' 제거 실패! queueKey: {}", memberId, queueKey);
            }
            return "예약이 마감되었습니다.";
        }

        // Redis List 사용. 대기열에 사용자 추가
        redisTemplate.opsForList().rightPush(queueKey, memberId);

        //대기열 만료 - 예약 가능 기간 동안 유지.
        // 키가 처음 생성될 때만 만료 시간 설정
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(queueKey))) {
            Store store = storeService.findById(storeId)
                    .orElseThrow(() -> new RuntimeException("Store not found for ID: " + storeId));
            LocalDateTime reservationEnd = store.getReservationFin();
            long secondsUntilExpiry = ChronoUnit.SECONDS.between(LocalDateTime.now(), reservationEnd);

            if (secondsUntilExpiry > 0) {
                redisTemplate.expire(queueKey, secondsUntilExpiry, TimeUnit.SECONDS);
                redisTemplate.expire(uniqueUsersKey, secondsUntilExpiry, TimeUnit.SECONDS);
                //log.info("대기열 키 만료 시간 설정 완료: {}초 후 만료", secondsUntilExpiry);
            }/* else {
                log.warn("예약 가능 기간이 이미 종료되었습니다. 키 만료 시간을 설정하지 않습니다.");
            }*/
        }

        // Pub/Sub 메시지 발행
        reservationPublisher.publishReservationEvent("reservationChannel",
                storeId + "|" + date + "|" + timeSlot + "|" + memberId);

        // 대기열에서 사용자 순서
        //Long position = redisTemplate.opsForList().size(queueKey);
        log.info("대기열에 추가되었습니다. 사용자 {} 현재 순번: {}", memberId, queue.size()+1);
        res+=" 대기열에 추가되었습니다. 사용자 "+memberId+" 현재 순번: "+(queue.size()+1);
        return res;
    }

    //대기열 상태 확인
    public String getQueueStatus(String date, String timeSlot, String memberId, int storeId) {
        String queueKey = REDIS_QUEUE_KEY + storeId + "|" + date + "|" + timeSlot;

        List<Object> queue = redisTemplate.opsForList().range(queueKey, 0, -1);

        if (queue == null || !queue.contains(memberId)) {
            //log.info("대기열에 없습니다.");
            return "아직 대기열에 없습니다.";
        }
        int position = queue.indexOf(memberId);
        int peopleBehind = queue.size() - position - 1;

        log.info("사용자 {}는 현재 {}번째입니다. 뒤에 {}명 있습니다.", memberId, position+1, peopleBehind);
        return "사용자 "+memberId+"는 현재 "+(position+1)+"번째입니다. 뒤에 "+peopleBehind+"명 있습니다.";
        /*return Map.of(
                "currentPosition", position + 1,
                "peopleBehind", peopleBehind,
                "queueSize", queue.size(),
                "userQueue", queue
        );*/
    }

    /*
    //예약 처리 스케줄러
    //@Scheduled(fixedRate = 5000) // 5초마다 실행
    public void processQueue() {
        ScanOptions scanOptions = ScanOptions.scanOptions()
                .match(AVAILABLE_SLOTS_KEY + "*") // 패턴에 맞는 키 검색
                .count(1000) // 한 번에 검색할 키 개수
                .build();

        try (Cursor<byte[]> cursor = redisTemplate.executeWithStickyConnection(
                redisConnection -> redisConnection.scan(scanOptions)
        )) {
            if (cursor == null) {
                log.warn("No keys found for pattern: {}", AVAILABLE_SLOTS_KEY + "*");
                return;
            }

            while (cursor.hasNext()) {
                String slotKey = new String(cursor.next()); // 키를 문자열로 변환
                processSlotKey(slotKey); // 개별 키 처리
            }
        } catch (Exception e) {
            log.error("Error during Redis SCAN operation", e);
        }
    }
    private void processSlotKey(String slotKey) {
        try {
            // 키에서 storeId, date, timeSlot 추출
            String[] keyParts = slotKey.split("\\|");
            int storeId = Integer.parseInt(keyParts[1]);
            String date = keyParts[2];
            String timeSlot = keyParts[3];

            // 관련 queueKey 계산
            String queueKey = slotKey.replace(AVAILABLE_SLOTS_KEY, REDIS_QUEUE_KEY);

            // 현재 가능한 슬롯 수 확인
            int availableSlots = Integer.parseInt((String) redisTemplate.opsForValue().get(slotKey));

            if (availableSlots > 0) {
                // 예약 처리
                processReservations(storeId, date, timeSlot, queueKey, slotKey, availableSlots);
            } else {
                // 대기열에서 사용자 처리
                processWaitingQueue(queueKey);
            }
        } catch (Exception e) {
            log.error("Error processing slot key: {}", slotKey, e);
        }
    }

    private void processReservations(int storeId, String date, String timeSlot, String queueKey, String slotKey, int availableSlots) {
        while (availableSlots > 0) {
            String nextUserId = (String) redisTemplate.opsForList().leftPop(queueKey);
            if (nextUserId == null) break;

            // Store 정보 조회
            Store store = storeService.findById(storeId)
                    .orElseThrow(() -> new RuntimeException("Store not found for ID: " + storeId));

            // 예약 생성 및 DB 저장
            long id = reservationRepository.save(Reservation.builder()
                    .date(date)
                    .timeSlot(timeSlot)
                    .user(nextUserId)
                    .status(ReservationStatus.COMPLETED)
                    .store(store)
                    .build()).getId();

            availableSlots--;
            redisTemplate.opsForValue().set(slotKey, String.valueOf(availableSlots));
            log.info("예약 완료: 사용자 {} 남은 슬롯: {}", nextUserId, availableSlots);

            // 알림 전송
//            notificationService.createScheduleAsync(id);
        }
    }

    private void processWaitingQueue(String queueKey) {
        if (redisTemplate.opsForList().size(queueKey) > 0) {
            String nextUserId = (String) redisTemplate.opsForList().leftPop(queueKey);
            if (nextUserId != null) {
                log.info("예약이 마감되었습니다: 사용자 {}", nextUserId);
            }
        }
    }
*/
    //예약 정보 상세 조회
    public Optional<Reservation> getReservation(Long reservationId){
        //현재 로그인한 사용자 권한 확인
        return reservationRepository.findById(reservationId);
    }

    //예약 내역 삭제
    public void deleteReservation(Long reservationId){
        reservationRepository.deleteById(reservationId);
    }

    //예약 취소(내역에 남음)
    @Transactional
    public void cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new EntityNotFoundException("Reservation not found for ID: " + reservationId));

        String slotKey = AVAILABLE_SLOTS_KEY + reservation.getStore().getStoreId() + "|" + reservation.getDate() + "|" + reservation.getTimeSlot();
        String uniqueUsersKey = UNIQUE_USERS_KEY + reservation.getStore().getStoreId() + "|" + reservation.getDate() + "|" + reservation.getTimeSlot();

        if (Boolean.TRUE.equals(redisTemplate.hasKey(slotKey))) {
            reservationRepository.updateStatus(reservationId, ReservationStatus.CANCELED);

            // 예약 가능 슬롯 증가
            String slotValue = (String) redisTemplate.opsForValue().get(slotKey);
            int availableSlots = slotValue != null ? Integer.parseInt(slotValue) : 0;
            availableSlots++;
            redisTemplate.opsForValue().set(slotKey, String.valueOf(availableSlots));

            // Redis에서 사용자 제거 (HDEL 사용)
            Long removedCount = redisTemplate.opsForHash().delete(uniqueUsersKey, reservation.getUser());
            if (removedCount > 0) {
                log.info("✅ [예약 취소] 사용자 '{}'가 Redis Hash에서 제거됨.", reservation.getUser());
            } else {
                log.warn("🚨 [예약 취소] 사용자 '{}' 제거 실패! uniqueUsersKey: {}", reservation.getUser(), uniqueUsersKey);
            }
        } else {
            log.info("🚨 [예약 취소] 예약 취소 가능 기간이 아닙니다.");
        }
    }


    public List<Reservation> getReservationList(String user) {
        return reservationRepository.findByUser(user);
    }
}
