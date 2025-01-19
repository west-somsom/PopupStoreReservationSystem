package com.westsomsom.finalproject.notification.api;

import com.westsomsom.finalproject.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/api/notification/{notificationId}")
    public ResponseEntity<?> createScheduleAsync(@PathVariable(value = "notificationId") Long notificationId) {
        CompletableFuture<Boolean> result = notificationService.createScheduleAsync(notificationId);
        result.thenAccept(success -> {
            if (!success) {
                throw new RuntimeException("Failed to create schedule");
            }
        });

        return ResponseEntity.ok().body("success");
    }
}
