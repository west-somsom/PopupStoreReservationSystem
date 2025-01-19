package com.westsomsom.finalproject.notification.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDto {
    private String storeName;
    private String date;
    private String toEmail;
}
