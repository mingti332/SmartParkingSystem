package com.parking.dao;

import com.parking.entity.Reservation;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface ReservationDao {
    boolean hasConflict(Connection conn, Long spaceId, LocalDateTime start, LocalDateTime end) throws SQLException;

    long insert(Connection conn, Reservation reservation) throws SQLException;

    int cancelById(Connection conn, Long reservationId, Long userId) throws SQLException;

    Reservation findById(Connection conn, Long reservationId) throws SQLException;

    List<Reservation> findByUser(Long userId, int pageNo, int pageSize) throws SQLException;

    List<Reservation> findByOwner(Long ownerId, int pageNo, int pageSize) throws SQLException;
}
