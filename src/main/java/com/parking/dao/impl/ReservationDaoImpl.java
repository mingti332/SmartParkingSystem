package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.ReservationDao;
import com.parking.entity.Reservation;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ReservationDaoImpl implements ReservationDao {
    @Override
    public boolean hasConflict(Connection conn, Long spaceId, LocalDateTime start, LocalDateTime end) throws SQLException {
        String sql = """
                SELECT COUNT(1)
                FROM Reservations
                WHERE space_id = ?
                  AND status IN ('PENDING','ACTIVE')
                  AND ? < reserve_end
                  AND ? > reserve_start
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, spaceId);
            ps.setTimestamp(2, Timestamp.valueOf(start));
            ps.setTimestamp(3, Timestamp.valueOf(end));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    @Override
    public long insert(Connection conn, Reservation reservation) throws SQLException {
        String sql = """
                INSERT INTO Reservations(user_id, space_id, reserve_start, reserve_end, status, create_time)
                VALUES (?, ?, ?, ?, ?, NOW())
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, reservation.getUserId());
            ps.setLong(2, reservation.getSpaceId());
            ps.setTimestamp(3, Timestamp.valueOf(reservation.getReserveStart()));
            ps.setTimestamp(4, Timestamp.valueOf(reservation.getReserveEnd()));
            ps.setString(5, reservation.getStatus());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    long generatedId = keys.getLong(1);
                    // Defensive uniqueness check for reservation_id.
                    if (!isReservationIdUnique(conn, generatedId)) {
                        throw new SQLException("reservation_id duplicated: " + generatedId);
                    }
                    return generatedId;
                }
            }
        }
        throw new SQLException("创建预约失败");
    }

    private boolean isReservationIdUnique(Connection conn, long reservationId) throws SQLException {
        String sql = "SELECT COUNT(1) FROM Reservations WHERE reservation_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) == 1;
                }
            }
        }
        return false;
    }

    @Override
    public int cancelById(Connection conn, Long reservationId, Long userId) throws SQLException {
        String sql = """
                UPDATE Reservations
                SET status = 'CANCELED', cancel_time = NOW()
                WHERE reservation_id = ? AND user_id = ? AND status = 'PENDING'
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reservationId);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        }
    }

    @Override
    public Reservation findById(Connection conn, Long reservationId) throws SQLException {
        String sql = """
                SELECT reservation_id, user_id, space_id, reserve_start, reserve_end, status, create_time, cancel_time
                FROM Reservations
                WHERE reservation_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, reservationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Reservation reservation = new Reservation();
                    reservation.setReservationId(rs.getLong("reservation_id"));
                    reservation.setUserId(rs.getLong("user_id"));
                    reservation.setSpaceId(rs.getLong("space_id"));
                    reservation.setReserveStart(rs.getTimestamp("reserve_start").toLocalDateTime());
                    reservation.setReserveEnd(rs.getTimestamp("reserve_end").toLocalDateTime());
                    reservation.setStatus(rs.getString("status"));
                    reservation.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
                    Timestamp cancelTs = rs.getTimestamp("cancel_time");
                    if (cancelTs != null) {
                        reservation.setCancelTime(cancelTs.toLocalDateTime());
                    }
                    return reservation;
                }
            }
        }
        return null;
    }

    @Override
    public List<Reservation> findByUser(Long userId, int pageNo, int pageSize) throws SQLException {
        int offset = (pageNo - 1) * pageSize;
        String sql = """
                SELECT reservation_id, user_id, space_id, reserve_start, reserve_end, status, create_time, cancel_time
                FROM Reservations
                WHERE user_id = ?
                ORDER BY reservation_id DESC
                LIMIT ? OFFSET ?
                """;
        List<Reservation> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setInt(2, pageSize);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation reservation = new Reservation();
                    reservation.setReservationId(rs.getLong("reservation_id"));
                    reservation.setUserId(rs.getLong("user_id"));
                    reservation.setSpaceId(rs.getLong("space_id"));
                    reservation.setReserveStart(rs.getTimestamp("reserve_start").toLocalDateTime());
                    reservation.setReserveEnd(rs.getTimestamp("reserve_end").toLocalDateTime());
                    reservation.setStatus(rs.getString("status"));
                    reservation.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
                    Timestamp cancelTs = rs.getTimestamp("cancel_time");
                    if (cancelTs != null) {
                        reservation.setCancelTime(cancelTs.toLocalDateTime());
                    }
                    list.add(reservation);
                }
            }
        }
        return list;
    }

    @Override
    public List<Reservation> findByOwner(Long ownerId, int pageNo, int pageSize) throws SQLException {
        int offset = (pageNo - 1) * pageSize;
        String sql = """
                SELECT r.reservation_id, r.user_id, r.space_id, r.reserve_start, r.reserve_end, r.status, r.create_time, r.cancel_time
                FROM Reservations r
                INNER JOIN ParkingSpaces s ON s.space_id = r.space_id
                WHERE s.owner_id = ?
                ORDER BY r.reservation_id DESC
                LIMIT ? OFFSET ?
                """;
        List<Reservation> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerId);
            ps.setInt(2, pageSize);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Reservation reservation = new Reservation();
                    reservation.setReservationId(rs.getLong("reservation_id"));
                    reservation.setUserId(rs.getLong("user_id"));
                    reservation.setSpaceId(rs.getLong("space_id"));
                    reservation.setReserveStart(rs.getTimestamp("reserve_start").toLocalDateTime());
                    reservation.setReserveEnd(rs.getTimestamp("reserve_end").toLocalDateTime());
                    reservation.setStatus(rs.getString("status"));
                    reservation.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
                    Timestamp cancelTs = rs.getTimestamp("cancel_time");
                    if (cancelTs != null) {
                        reservation.setCancelTime(cancelTs.toLocalDateTime());
                    }
                    list.add(reservation);
                }
            }
        }
        return list;
    }
}