package com.westsomsom.finalproject.reservation.domain;

import com.westsomsom.finalproject.store.domain.Store;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class Reservation {
    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    private int id;

    @CreatedDate
    private LocalDateTime reservationDate;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status;

    @Column(nullable = false)
    private String date;
    @Column(nullable = false)
    private String timeSlot;

    private String user;
    /*
    @ManyToOne
    @JoinColumn(name = "user_id")
    private UserInfo user;
    */
    @ManyToOne
    @JoinColumn(name = "store_id", referencedColumnName = "storeId", nullable = false)
    private Store store;

}
