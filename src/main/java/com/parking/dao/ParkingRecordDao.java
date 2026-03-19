package com.parking.dao;

import com.parking.entity.ParkingRecord;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface ParkingRecordDao {
    long insertEntry(Connection conn, Long reservationId, Long userId, Long spaceId, LocalDateTime entryTime) throws SQLException;

    ParkingRecord findById(Connection conn, Long recordId) throws SQLException;

    int completeExit(Connection conn, Long recordId, LocalDateTime exitTime, long durationMinutes, java.math.BigDecimal fee) throws SQLException;

    List<ParkingRecord> findByUser(Long userId, int pageNo, int pageSize) throws SQLException;

    List<ParkingRecord> search(Long userId, Long spaceId, int pageNo, int pageSize) throws SQLException;
}
