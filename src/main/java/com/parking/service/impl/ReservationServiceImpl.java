package com.parking.service.impl;

import com.parking.config.DbUtil;
import com.parking.dao.ParkingSpaceDao;
import com.parking.dao.ReservationDao;
import com.parking.dao.impl.ParkingSpaceDaoImpl;
import com.parking.dao.impl.ReservationDaoImpl;
import com.parking.entity.Reservation;
import com.parking.service.ReservationService;
import com.parking.service.ServiceException;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
//预约模块
public class ReservationServiceImpl implements ReservationService {
    private final ReservationDao reservationDao = new ReservationDaoImpl();
    private final ParkingSpaceDao parkingSpaceDao = new ParkingSpaceDaoImpl();

    @Override
    public long reserve(Long userId, Long spaceId, LocalDateTime reserveStart, LocalDateTime reserveEnd) throws SQLException {
        if (reserveStart == null || reserveEnd == null || !reserveStart.isBefore(reserveEnd)) {
            throw new ServiceException("预约时间区间无效");
        }
        if (reserveStart.isBefore(LocalDateTime.now())) {
            throw new ServiceException("预约失败，输入时间有误：开始时间不能早于当前时间");
        }

        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                boolean conflict = reservationDao.hasConflict(conn, spaceId, reserveStart, reserveEnd);
                if (conflict) {
                    throw new ServiceException("预约失败：时间段冲突");
                }

                Reservation reservation = new Reservation();
                reservation.setUserId(userId);
                reservation.setSpaceId(spaceId);
                reservation.setReserveStart(reserveStart);
                reservation.setReserveEnd(reserveEnd);
                reservation.setStatus("PENDING");
                long id = reservationDao.insert(conn, reservation);

                parkingSpaceDao.updateStatus(conn, spaceId, "RESERVED");
                conn.commit();
                return id;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public void cancel(Long reservationId, Long userId, Long spaceId) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                int affected = reservationDao.cancelById(conn, reservationId, userId);
                if (affected == 0) {
                    throw new ServiceException("取消失败：未找到可取消的预约");
                }
                parkingSpaceDao.updateStatus(conn, spaceId, "FREE");
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<Reservation> getMyReservations(Long userId, int pageNo, int pageSize) throws SQLException {
        return reservationDao.findByUser(userId, pageNo, pageSize);
    }

    @Override
    public List<Reservation> getOwnerReservations(Long ownerId, int pageNo, int pageSize) throws SQLException {
        return reservationDao.findByOwner(ownerId, pageNo, pageSize);
    }
}
