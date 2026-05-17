package com.parking.service;

import com.parking.entity.ParkingLot;

import java.sql.SQLException;
import java.util.List;

public interface ParkingLotService {
    long addLot(ParkingLot lot) throws SQLException;

    void updateLot(ParkingLot lot) throws SQLException;

    void removeLot(Long lotId) throws SQLException;

    void updateLotField(Long lotId, String fieldName, String fieldValue) throws SQLException;

    List<ParkingLot> queryLots(String keyword, int pageNo, int pageSize) throws SQLException;
}
