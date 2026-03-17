package com.parking.service.impl;

import com.parking.config.DbUtil;
import com.parking.dao.*;
import com.parking.dao.impl.*;
import com.parking.entity.ParkingRecord;
import com.parking.entity.ParkingSpace;
import com.parking.entity.PricingRule;
import com.parking.entity.Reservation;
import com.parking.service.ParkingRecordService;
import com.parking.service.ServiceException;
import com.parking.service.pricing.FixedPricingStrategy;
import com.parking.service.pricing.HourlyPricingStrategy;
import com.parking.service.pricing.PricingStrategy;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
//停车与支付模块
public class ParkingRecordServiceImpl implements ParkingRecordService {
    private final ParkingRecordDao parkingRecordDao = new ParkingRecordDaoImpl();
    private final ParkingSpaceDao parkingSpaceDao = new ParkingSpaceDaoImpl();
    private final PricingRuleDao pricingRuleDao = new PricingRuleDaoImpl();
    private final ReservationDao reservationDao = new ReservationDaoImpl();
    private final PaymentRecordDao paymentRecordDao = new PaymentRecordDaoImpl();
    private final RevenueRecordDao revenueRecordDao = new RevenueRecordDaoImpl();

    @Override
    public long entry(Long reservationId, Long userId, Long spaceId, LocalDateTime entryTime) throws SQLException {
        if (userId == null || spaceId == null) {
            throw new ServiceException("用户ID和车位ID不能为空");
        }
        LocalDateTime now = entryTime == null ? LocalDateTime.now() : entryTime;
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ParkingSpace space = parkingSpaceDao.findById(conn, spaceId);
                if (space == null) {
                    throw new ServiceException("未找到对应车位");
                }
                if ("OCCUPIED".equalsIgnoreCase(space.getStatus())) {
                    throw new ServiceException("该车位当前已占用，无法入场");
                }

                boolean passByReservation = false;
                if (reservationId != null) {
                    Reservation reservation = reservationDao.findById(conn, reservationId);
                    if (reservation != null
                            && userId.equals(reservation.getUserId())
                            && spaceId.equals(reservation.getSpaceId())
                            && ("PENDING".equalsIgnoreCase(reservation.getStatus()) || "ACTIVE".equalsIgnoreCase(reservation.getStatus()))
                            && !now.isBefore(reservation.getReserveStart())
                            && now.isBefore(reservation.getReserveEnd())) {
                        passByReservation = true;
                    }
                }

                if (!passByReservation) {
                    boolean conflictNow = reservationDao.hasConflict(conn, spaceId, now, now.plusMinutes(1));
                    // 以实时冲突为准：预约时段已过即视为可重新开放，不再被历史 RESERVED 状态阻塞。
                    if (conflictNow) {
                        throw new ServiceException("该车位当前预约已满，请选择其他车位或先预约");
                    }
                    if (space.getShareStartTime() != null && now.toLocalTime().isBefore(space.getShareStartTime())) {
                        throw new ServiceException("当前未到该车位共享开始时间");
                    }
                    if (space.getShareEndTime() != null && !now.toLocalTime().isBefore(space.getShareEndTime())) {
                        throw new ServiceException("当前已超过该车位共享结束时间");
                    }
                }

                long recordId = parkingRecordDao.insertEntry(
                        conn, reservationId, userId, spaceId, now);
                parkingSpaceDao.updateStatus(conn, spaceId, "OCCUPIED");
                conn.commit();
                return recordId;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public BigDecimal exitAndPay(Long recordId, String payMethod) throws SQLException {
        try (Connection conn = DbUtil.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ParkingRecord record = parkingRecordDao.findById(conn, recordId);
                if (record == null || record.getExitTime() != null) {
                    throw new ServiceException("停车记录无效或已经完成出场");
                }

                ParkingSpaceDao.SpaceMeta meta = parkingSpaceDao.findMetaById(conn, record.getSpaceId());
                if (meta == null) {
                    throw new ServiceException("未找到对应车位");
                }

                PricingRule rule = pricingRuleDao.findActiveBySpaceType(conn, meta.getType());
                if (rule == null) {
                    throw new ServiceException("未找到该车位类型可用的计费规则：" + meta.getType());
                }

                LocalDateTime exitTime = LocalDateTime.now();
                long minutes = Duration.between(record.getEntryTime(), exitTime).toMinutes();
                if (minutes <= 0) {
                    minutes = 1;
                }

                PricingStrategy strategy = "FIXED".equalsIgnoreCase(rule.getChargeType())
                        ? new FixedPricingStrategy()
                        : new HourlyPricingStrategy();
                BigDecimal fee = strategy.calculate(minutes, rule);

                int updated = parkingRecordDao.completeExit(conn, recordId, exitTime, minutes, fee);
                if (updated == 0) {
                    throw new ServiceException("停车出场处理失败");
                }

                long paymentId = paymentRecordDao.insertPaid(conn, record.getRecordId(), record.getUserId(), fee,
                        payMethod == null ? "CASH" : payMethod);
                revenueRecordDao.insertUnsettled(conn, meta.getOwnerId(), record.getSpaceId(), paymentId, fee);
                parkingSpaceDao.updateStatus(conn, record.getSpaceId(), "FREE");

                conn.commit();
                return fee;
            } catch (Exception e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    @Override
    public List<ParkingRecord> getMyParkingRecords(Long userId, int pageNo, int pageSize) throws SQLException {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        return parkingRecordDao.findByUser(userId, pageNo, pageSize);
    }

    @Override
    public List<ParkingRecord> searchParkingRecords(Long userId, Long spaceId, int pageNo, int pageSize) throws SQLException {
        return parkingRecordDao.search(userId, spaceId, pageNo, pageSize);
    }
}
