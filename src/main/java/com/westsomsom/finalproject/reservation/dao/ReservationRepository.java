package com.westsomsom.finalproject.reservation.dao;

import com.westsomsom.finalproject.reservation.domain.Reservation;
import com.westsomsom.finalproject.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    @Modifying
    @Query("UPDATE Reservation r SET r.status = :status WHERE r.id=:reservationId")
    void updateStatus(Long reservationId, ReservationStatus status);

    @Query("SELECT r FROM Reservation r WHERE r.user=:user")
    List<Reservation> findByUser(String user);

    @Query("SELECT r FROM Reservation r WHERE r.date=:date AND r.timeSlot=:timeSlot")
    List<Reservation> findByDateAndTimeSlot(String date, String timeSlot);
}

