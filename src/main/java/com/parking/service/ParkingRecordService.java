package com.parking.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import com.parking.entity.ParkingRecord;

public interface ParkingRecordService {
    long entry(Long reservationId, Long userId, Long spaceId, LocalDateTime entryTime) throws SQLException;

    BigDecimal exitAndPay(Long recordId, String payMethod) throws SQLException;

    List<ParkingRecord> getMyParkingRecords(Long userId, int pageNo, int pageSize) throws SQLException;

    List<ParkingRecord> searchParkingRecords(Long userId, Long spaceId, int pageNo, int pageSize) throws SQLException;
}
