package com.parking.dao;

import com.parking.entity.ParkingLot;

import java.sql.SQLException;
import java.util.List;

public interface ParkingLotDao {
    long insert(ParkingLot lot) throws SQLException;

    int update(ParkingLot lot) throws SQLException;

    int deleteById(Long lotId) throws SQLException;

    ParkingLot findById(Long lotId) throws SQLException;

    List<ParkingLot> search(String keyword, int pageNo, int pageSize) throws SQLException;
}
