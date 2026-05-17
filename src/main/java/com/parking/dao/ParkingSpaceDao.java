package com.parking.dao;

import com.parking.entity.ParkingSpace;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface ParkingSpaceDao {
    long insert(ParkingSpace space) throws SQLException;

    int update(ParkingSpace space) throws SQLException;

    int deleteById(Long spaceId) throws SQLException;

    int updateSpaceField(Long spaceId, String fieldName, Object value) throws SQLException;

    List<ParkingSpace> search(String keyword, String status, Long lotId, int pageNo, int pageSize) throws SQLException;

    List<ParkingSpace> findByOwner(Long ownerId, int pageNo, int pageSize) throws SQLException;

    int updateShareWindow(Long spaceId, Long ownerId, LocalTime shareStart, LocalTime shareEnd) throws SQLException;

    List<ParkingSpace> findAvailableByTime(LocalDateTime reserveStart, LocalDateTime reserveEnd, int pageNo, int pageSize) throws SQLException;

    int updateStatus(Connection conn, Long spaceId, String status) throws SQLException;

    ParkingSpace findById(Long spaceId) throws SQLException;

    ParkingSpace findById(Connection conn, Long spaceId) throws SQLException;

    SpaceMeta findMetaById(Connection conn, Long spaceId) throws SQLException;

    class SpaceMeta {
        private final String type;
        private final Long ownerId;

        public SpaceMeta(String type, Long ownerId) {
            this.type = type;
            this.ownerId = ownerId;
        }

        public String getType() {
            return type;
        }

        public Long getOwnerId() {
            return ownerId;
        }
    }
}
