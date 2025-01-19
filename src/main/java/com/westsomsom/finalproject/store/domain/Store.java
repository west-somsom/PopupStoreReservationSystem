package com.westsomsom.finalproject.store.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Getter
@Table(name = "Store", indexes = @Index(name = "store_category_idx", columnList = "store_category"))
public class Store {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int storeId;

    @Column(nullable = false, length = 20)
    private String storeName;

    @Column(nullable = false, length = 100)
    private String storeBio;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate finDate;

    @Column(nullable = false, length = 20)
    private String storeCategory;

    @Column(nullable = false, length = 100)
    private String storeLoc;

    @Column(nullable = false)
    private LocalDateTime reservationStart;

    @Column(nullable = false)
    private LocalDateTime reservationFin;

    @Builder
    public Store(String storeName, String storeBio, LocalDate startDate, LocalDate finDate, String storeCategory, String storeLoc
    , LocalDateTime reservationStart, LocalDateTime reservationFin) {
        this.storeName = storeName;
        this.storeBio = storeBio;
        this.startDate = startDate;
        this.finDate = finDate;
        this.storeCategory = storeCategory;
        this.storeLoc = storeLoc;
        this.reservationStart = reservationStart;
        this.reservationFin = reservationFin;
    }
}
