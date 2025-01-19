package com.westsomsom.finalproject.notification.application;

import com.westsomsom.finalproject.notification.dto.NotificationDto;
import com.westsomsom.finalproject.reservation.dao.ReservationRepository;
import com.westsomsom.finalproject.reservation.domain.Reservation;
import lombok.RequiredArgsConstructor;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.scheduler.SchedulerAsyncClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SchedulerAsyncClient schedulerAsyncClient;

    private final ReservationRepository reservationRepository;

    @Value("${custom.scheduler.targetArn}")
    private String targetArn;

    @Value("${custom.scheduler.roleArn}")
    private String roleArn;

    public CompletableFuture<Boolean> createScheduleAsync(Long reservationId) {
        // reservation 객체 찾기
        Reservation reservation = reservationRepository.findById(reservationId).orElseThrow(IllegalArgumentException::new);

        // 알림 데이터 생성
        String input = createInput(reservation);

        // 스케줄 시간 설정 -> 현재 시간에서 10초 뒤로 설정
        String scheduleTime = calculateScheduleTime(10, ChronoUnit.SECONDS);

        CompletableFuture<Boolean> scheduleResult = CreateAndRequestcreateSchedule(input, scheduleTime);

        // 예약 시간이 현재 시간으로부터 24시간 이상 차이나는 경우
        if(Duration.between(LocalDateTime.now(), reservation.getReservationDate()).toHours() > 24) {
            // 24시간 뒤 실행되는 요청 생성
            String delayedScheduleTime = calculateScheduleTime(24, ChronoUnit.HOURS);
            CompletableFuture<Boolean> delayedResult = CreateAndRequestcreateSchedule(input, delayedScheduleTime);

            return scheduleResult.thenCombine(delayedResult, (first, second) -> first && second);
        }

        return scheduleResult;
    }

    // 비동기 요청 생성 및 발송 메소드
    private CompletableFuture<Boolean> CreateAndRequestcreateSchedule(String input, String scheduleTime) {
        // 요청 생성
        CreateScheduleRequest request = buildCreateSchedule(input, scheduleTime);
        // 요청 발송
        return schedulerAsyncClient.createSchedule(request)
                .thenApply(response -> true)
                .exceptionally(ex -> {
                    if (ex instanceof ConflictException) {
                        throw new CompletionException("Conflict occurred while creating schedule", ex);
                    } else {
                        throw new CompletionException("Error creating schedule", ex);
                    }
                });
    }

    // 요청 시간 생성 메소드
    private String calculateScheduleTime(long amount, ChronoUnit unit) {
        return Instant.now().plus(amount, unit)
                .atZone(java.time.ZoneId.of("UTC"))
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    // 요청 input 생성 메소드
    private String createInput(Reservation reservation) {
        NotificationDto notificationDto = NotificationDto.builder()
                .storeName(reservation.getStore().getStoreName())
                .date(reservation.getReservationDate().toString())
                .toEmail(reservation.getUser())
                .build();

        Map<String, Object> map = new HashMap<>();
        map.put("storeName", notificationDto.getStoreName());
        map.put("date", notificationDto.getDate());
        map.put("toEmail", notificationDto.getToEmail());

        return new JSONObject(map).toJSONString();
    }

    // 요청 생성 메소드
    private CreateScheduleRequest buildCreateSchedule(String input, String scheduleTime) {
        Target target = Target.builder()
                .arn(targetArn)
                .roleArn(roleArn)
                .input(input)
                .build();

        return CreateScheduleRequest.builder()
                .name(UUID.randomUUID().toString())
                .scheduleExpression("at(" + scheduleTime + ")")
                .groupName("default")
                .target(target)
                .actionAfterCompletion(ActionAfterCompletion.DELETE)
                .flexibleTimeWindow(FlexibleTimeWindow.builder()
                        .mode(FlexibleTimeWindowMode.OFF)
                        .build())
                .build();
    }
}
