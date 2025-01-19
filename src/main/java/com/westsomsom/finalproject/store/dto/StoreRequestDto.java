package com.westsomsom.finalproject.store.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
public class StoreRequestDto {

    private String storeName;
    private String storeBio;
    private LocalDate startDate;
    private LocalDate finDate;
    private String storeCategory;
    private String storeLoc;
    private LocalDateTime reservationStart;
    private LocalDateTime reservationFin;
}
