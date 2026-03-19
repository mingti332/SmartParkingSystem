package com.parking.service;

import com.parking.entity.ParkingSpace;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public interface ParkingSpaceService {
    long addSpace(ParkingSpace space) throws SQLException;

    void updateSpace(ParkingSpace space) throws SQLException;

    void removeSpace(Long spaceId) throws SQLException;

    List<ParkingSpace> querySpaces(String keyword, String status, Long lotId, int pageNo, int pageSize) throws SQLException;

    List<ParkingSpace> queryMySpaces(Long ownerId, int pageNo, int pageSize) throws SQLException;

    void updateMyShareWindow(Long spaceId, Long ownerId, LocalTime shareStart, LocalTime shareEnd) throws SQLException;

    List<ParkingSpace> queryAvailableSpaces(LocalDateTime reserveStart, LocalDateTime reserveEnd, int pageNo, int pageSize) throws SQLException;
}
