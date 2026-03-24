package com.parking.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import com.parking.entity.ParkingRecord;

public interface ParkingRecordService {
    class AutoEntryResult {
        private final long recordId;
        private final long spaceId;

        public AutoEntryResult(long recordId, long spaceId) {
            this.recordId = recordId;
            this.spaceId = spaceId;
        }

        public long getRecordId() {
            return recordId;
        }

        public long getSpaceId() {
            return spaceId;
        }
    }

    long entry(Long reservationId, Long userId, Long spaceId, LocalDateTime entryTime) throws SQLException;

    AutoEntryResult entryByLotAndType(Long reservationId, Long userId, Long lotId, String spaceType, LocalDateTime entryTime) throws SQLException;

    BigDecimal exitAndPay(Long recordId, String payMethod) throws SQLException;

    List<ParkingRecord> getMyParkingRecords(Long userId, int pageNo, int pageSize) throws SQLException;

    List<ParkingRecord> searchParkingRecords(Long userId, Long spaceId, int pageNo, int pageSize) throws SQLException;
}
