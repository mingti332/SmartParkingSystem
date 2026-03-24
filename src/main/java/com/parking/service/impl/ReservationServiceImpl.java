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
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
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
    public AutoReserveResult reserveByLotAndType(Long userId, Long lotId, String spaceType, LocalDateTime reserveStart, LocalDateTime reserveEnd) throws SQLException {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        if (lotId == null) {
            throw new ServiceException("停车场ID不能为空");
        }
        if (reserveStart == null || reserveEnd == null || !reserveStart.isBefore(reserveEnd)) {
            throw new ServiceException("预约时间区间无效");
        }
        if (reserveStart.isBefore(LocalDateTime.now())) {
            throw new ServiceException("预约失败，输入时间有误：开始时间不能早于当前时间");
        }
        String typeCode = normalizeSpaceType(spaceType);

        List<com.parking.entity.ParkingSpace> matchedSpaces = loadSpacesByLotAndType(lotId, typeCode);
        if (matchedSpaces.isEmpty()) {
            throw new ServiceException("该停车场不存在该类型车位");
        }

        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Long selectedSpaceId = pickReservableSpace(conn, matchedSpaces, reserveStart, reserveEnd);
                if (selectedSpaceId == null) {
                    boolean allOccupied = matchedSpaces.stream().allMatch(s -> "OCCUPIED".equalsIgnoreCase(s.getStatus()));
                    if (allOccupied) {
                        throw new ServiceException("该类型车位已满");
                    }
                    throw new ServiceException("该类型车位在所选时段已预约满");
                }

                Reservation reservation = new Reservation();
                reservation.setUserId(userId);
                reservation.setSpaceId(selectedSpaceId);
                reservation.setReserveStart(reserveStart);
                reservation.setReserveEnd(reserveEnd);
                reservation.setStatus("PENDING");
                long id = reservationDao.insert(conn, reservation);

                parkingSpaceDao.updateStatus(conn, selectedSpaceId, "RESERVED");
                conn.commit();
                return new AutoReserveResult(id, selectedSpaceId);
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
        cancelInternal(reservationId, userId, spaceId);
    }

    @Override
    public void cancel(Long reservationId, Long userId) throws SQLException {
        cancelInternal(reservationId, userId, null);
    }

    private void cancelInternal(Long reservationId, Long userId, Long spaceId) throws SQLException {
        if (reservationId == null || userId == null) {
            throw new ServiceException("取消失败：未找到可取消的预约");
        }
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                Reservation target = reservationDao.findById(conn, reservationId);
                if (target == null || !userId.equals(target.getUserId())) {
                    throw new ServiceException("取消失败：未找到可取消的预约");
                }
                int affected = reservationDao.cancelById(conn, reservationId, userId);
                if (affected == 0) {
                    throw new ServiceException("取消失败：未找到可取消的预约");
                }
                Long finalSpaceId = spaceId != null ? spaceId : target.getSpaceId();
                if (finalSpaceId != null) {
                    parkingSpaceDao.updateStatus(conn, finalSpaceId, "FREE");
                }
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

    private String normalizeSpaceType(String input) {
        String v = input == null ? "" : input.trim().toUpperCase();
        if ("GROUND".equals(v) || "UNDERGROUND".equals(v)) {
            return v;
        }
        if ("地上".equals(input)) return "GROUND";
        if ("地下".equals(input)) return "UNDERGROUND";
        throw new ServiceException("车位类型不能为空");
    }

    private List<com.parking.entity.ParkingSpace> loadSpacesByLotAndType(Long lotId, String typeCode) throws SQLException {
        final int pageSize = 200;
        int pageNo = 1;
        List<com.parking.entity.ParkingSpace> all = new java.util.ArrayList<>();
        while (true) {
            List<com.parking.entity.ParkingSpace> page = parkingSpaceDao.search("", "", lotId, pageNo, pageSize);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            pageNo++;
            if (pageNo > 200) break;
        }
        return all.stream()
                .filter(s -> typeCode.equalsIgnoreCase(s.getType()))
                .sorted(Comparator.comparingLong(s -> s.getSpaceId() == null ? Long.MAX_VALUE : s.getSpaceId()))
                .collect(Collectors.toList());
    }

    private Long pickReservableSpace(Connection conn,
                                     List<com.parking.entity.ParkingSpace> candidates,
                                     LocalDateTime reserveStart,
                                     LocalDateTime reserveEnd) throws SQLException {
        LocalTime start = reserveStart.toLocalTime();
        LocalTime end = reserveEnd.toLocalTime();
        for (com.parking.entity.ParkingSpace s : candidates) {
            if (s.getSpaceId() == null) continue;
            if ("OCCUPIED".equalsIgnoreCase(s.getStatus())) continue;
            if (s.getShareStartTime() != null && start.isBefore(s.getShareStartTime())) continue;
            if (s.getShareEndTime() != null && end.isAfter(s.getShareEndTime())) continue;
            if (!reservationDao.hasConflict(conn, s.getSpaceId(), reserveStart, reserveEnd)) {
                return s.getSpaceId();
            }
        }
        return null;
    }
}
