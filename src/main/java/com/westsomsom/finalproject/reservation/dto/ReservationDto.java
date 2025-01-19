package com.westsomsom.finalproject.reservation.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationDto {
    String date;
    String timeSlot;
    String memberId;
    int storeId;
}
