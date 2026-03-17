package com.parking.service;

import com.parking.entity.Reservation;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface ReservationService {
    long reserve(Long userId, Long spaceId, LocalDateTime reserveStart, LocalDateTime reserveEnd) throws SQLException;

    void cancel(Long reservationId, Long userId, Long spaceId) throws SQLException;

    List<Reservation> getMyReservations(Long userId, int pageNo, int pageSize) throws SQLException;

    List<Reservation> getOwnerReservations(Long ownerId, int pageNo, int pageSize) throws SQLException;
}
