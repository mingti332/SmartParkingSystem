package com.parking.service;

import com.parking.entity.Reservation;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public interface ReservationService {
    class AutoReserveResult {
        private final long reservationId;
        private final long spaceId;

        public AutoReserveResult(long reservationId, long spaceId) {
            this.reservationId = reservationId;
            this.spaceId = spaceId;
        }

        public long getReservationId() {
            return reservationId;
        }

        public long getSpaceId() {
            return spaceId;
        }
    }

    long reserve(Long userId, Long spaceId, LocalDateTime reserveStart, LocalDateTime reserveEnd) throws SQLException;

    AutoReserveResult reserveByLotAndType(Long userId, Long lotId, String spaceType, LocalDateTime reserveStart, LocalDateTime reserveEnd) throws SQLException;

    void cancel(Long reservationId, Long userId, Long spaceId) throws SQLException;

    void cancel(Long reservationId, Long userId) throws SQLException;

    List<Reservation> getMyReservations(Long userId, int pageNo, int pageSize) throws SQLException;

    List<Reservation> getOwnerReservations(Long ownerId, int pageNo, int pageSize) throws SQLException;
}
