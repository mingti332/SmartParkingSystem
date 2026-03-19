package com.parking.dao.impl;

import com.parking.dao.ParkingRecordDao;
import com.parking.entity.ParkingRecord;

import java.math.BigDecimal;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class ParkingRecordDaoImpl implements ParkingRecordDao {
    @Override
    public long insertEntry(Connection conn, Long reservationId, Long userId, Long spaceId, LocalDateTime entryTime) throws SQLException {
        String sql = """
                INSERT INTO ParkingRecords(reservation_id, user_id, space_id, entry_time)
                VALUES (?, ?, ?, ?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (reservationId == null) {
                ps.setNull(1, Types.BIGINT);
            } else {
                ps.setLong(1, reservationId);
            }
            ps.setLong(2, userId);
            ps.setLong(3, spaceId);
            ps.setTimestamp(4, Timestamp.valueOf(entryTime));
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("新增停车入场记录失败");
    }

    @Override
    public ParkingRecord findById(Connection conn, Long recordId) throws SQLException {
        String sql = """
                SELECT record_id, reservation_id, user_id, space_id, entry_time, exit_time, duration, fee
                FROM ParkingRecords
                WHERE record_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, recordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    ParkingRecord record = new ParkingRecord();
                    record.setRecordId(rs.getLong("record_id"));
                    long reservationId = rs.getLong("reservation_id");
                    if (!rs.wasNull()) {
                        record.setReservationId(reservationId);
                    }
                    record.setUserId(rs.getLong("user_id"));
                    record.setSpaceId(rs.getLong("space_id"));
                    record.setEntryTime(rs.getTimestamp("entry_time").toLocalDateTime());
                    Timestamp exit = rs.getTimestamp("exit_time");
                    if (exit != null) {
                        record.setExitTime(exit.toLocalDateTime());
                    }
                    long duration = rs.getLong("duration");
                    if (!rs.wasNull()) {
                        record.setDuration(duration);
                    }
                    record.setFee(rs.getBigDecimal("fee"));
                    return record;
                }
            }
        }
        return null;
    }

    @Override
    public int completeExit(Connection conn, Long recordId, LocalDateTime exitTime, long durationMinutes, BigDecimal fee) throws SQLException {
        String sql = """
                UPDATE ParkingRecords
                SET exit_time = ?, duration = ?, fee = ?
                WHERE record_id = ? AND exit_time IS NULL
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(exitTime));
            ps.setLong(2, durationMinutes);
            ps.setBigDecimal(3, fee);
            ps.setLong(4, recordId);
            return ps.executeUpdate();
        }
    }

    @Override
    public List<ParkingRecord> findByUser(Long userId, int pageNo, int pageSize) throws SQLException {
        return search(userId, null, pageNo, pageSize);
    }

    @Override
    public List<ParkingRecord> search(Long userId, Long spaceId, int pageNo, int pageSize) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT record_id, reservation_id, user_id, space_id, entry_time, exit_time, duration, fee
                FROM ParkingRecords
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (userId != null) {
            sql.append(" AND user_id = ? ");
            params.add(userId);
        }
        if (spaceId != null) {
            sql.append(" AND space_id = ? ");
            params.add(spaceId);
        }
        sql.append(" ORDER BY record_id DESC LIMIT ? OFFSET ? ");
        params.add(pageSize);
        params.add((pageNo - 1) * pageSize);

        List<ParkingRecord> rows = new ArrayList<>();
        try (Connection conn = com.parking.config.DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ParkingRecord record = new ParkingRecord();
                    record.setRecordId(rs.getLong("record_id"));
                    long reservationId = rs.getLong("reservation_id");
                    if (!rs.wasNull()) {
                        record.setReservationId(reservationId);
                    }
                    record.setUserId(rs.getLong("user_id"));
                    record.setSpaceId(rs.getLong("space_id"));
                    record.setEntryTime(rs.getTimestamp("entry_time").toLocalDateTime());
                    Timestamp exit = rs.getTimestamp("exit_time");
                    if (exit != null) {
                        record.setExitTime(exit.toLocalDateTime());
                    }
                    long duration = rs.getLong("duration");
                    if (!rs.wasNull()) {
                        record.setDuration(duration);
                    }
                    record.setFee(rs.getBigDecimal("fee"));
                    rows.add(record);
                }
            }
        }
        return rows;
    }
}
