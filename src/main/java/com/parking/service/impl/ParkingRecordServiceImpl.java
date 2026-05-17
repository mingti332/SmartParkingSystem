package com.parking.service.impl;

import com.parking.config.DbUtil;
import com.parking.dao.*;
import com.parking.dao.impl.*;
import com.parking.entity.ParkingLot;
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
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
//停车与支付模块
public class ParkingRecordServiceImpl implements ParkingRecordService {
    private final ParkingRecordDao parkingRecordDao = new ParkingRecordDaoImpl();
    private final ParkingSpaceDao parkingSpaceDao = new ParkingSpaceDaoImpl();
    private final ParkingLotDao parkingLotDao = new ParkingLotDaoImpl();
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

                ParkingLot lot = parkingLotDao.findById(space.getLotId());
                if (lot != null && !lot.is24Hour()
                        && lot.getOpenTime() != null && lot.getCloseTime() != null) {
                    LocalTime nowTime = now.toLocalTime();
                    if (nowTime.isBefore(lot.getOpenTime())) {
                        throw new ServiceException("当前未到停车场营业时间 " + lot.getOpenTime());
                    }
                    if (nowTime.isAfter(lot.getCloseTime())) {
                        throw new ServiceException("当前已过停车场营业结束时间 " + lot.getCloseTime());
                    }
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
                    if (space.getShareEndTime() != null && now.toLocalTime().isAfter(space.getShareEndTime())) {
                        throw new ServiceException("当前已超过该车位共享结束时间");
                    }
                }

                long recordId = parkingRecordDao.insertEntry(
                        conn, reservationId, userId, spaceId, now);
                parkingSpaceDao.updateStatus(conn, spaceId, "OCCUPIED");
                if (reservationId != null) {
                    reservationDao.updateStatus(conn, reservationId, "ACTIVE");
                }
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
    public AutoEntryResult entryByLotAndType(Long reservationId, Long userId, Long lotId, String spaceType, LocalDateTime entryTime) throws SQLException {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        if (lotId == null) {
            throw new ServiceException("停车场ID不能为空");
        }
        String typeCode = normalizeSpaceType(spaceType);
        LocalDateTime now = entryTime == null ? LocalDateTime.now() : entryTime;

        List<ParkingSpace> matched = loadSpacesByLotAndType(lotId, typeCode);
        if (matched.isEmpty()) {
            throw new ServiceException("该停车场不存在该类型车位");
        }

        Long selectedSpaceId;
        if (reservationId != null) {
            try (Connection conn = DbUtil.getConnection()) {
                Reservation reservation = reservationDao.findById(conn, reservationId);
                if (reservation == null || !userId.equals(reservation.getUserId())) {
                    throw new ServiceException("预约记录不存在或不属于当前用户");
                }
                ParkingSpace reservedSpace = parkingSpaceDao.findById(conn, reservation.getSpaceId());
                if (reservedSpace == null) {
                    throw new ServiceException("未找到对应车位");
                }
                if (!lotId.equals(reservedSpace.getLotId()) || !typeCode.equalsIgnoreCase(reservedSpace.getType())) {
                    throw new ServiceException("预约车位与所选停车场或类型不匹配");
                }
                selectedSpaceId = reservedSpace.getSpaceId();
            }
            long recordId = entry(reservationId, userId, selectedSpaceId, now);
            return new AutoEntryResult(recordId, selectedSpaceId);
        } else {
            List<Long> candidateSpaceIds = pickEntrySpaceCandidates(matched, now);
            if (candidateSpaceIds.isEmpty()) {
                boolean allOccupied = matched.stream().allMatch(s -> "OCCUPIED".equalsIgnoreCase(s.getStatus()));
                if (allOccupied) {
                    throw new ServiceException("该类型车位已满");
                }
                throw new ServiceException("该类型车位当前已预约或不可入场");
            }
            for (Long candidateSpaceId : candidateSpaceIds) {
                try {
                    long recordId = entry(null, userId, candidateSpaceId, now);
                    return new AutoEntryResult(recordId, candidateSpaceId);
                } catch (ServiceException ex) {
                    String m = ex.getMessage() == null ? "" : ex.getMessage();
                    if (m.contains("预约已满") || m.contains("已占用") || m.contains("未到该车位共享开始时间") || m.contains("已超过该车位共享结束时间")) {
                        continue;
                    }
                    throw ex;
                }
            }
            throw new ServiceException("该类型车位当前已预约或不可入场");
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
                if (record.getReservationId() != null) {
                    reservationDao.updateStatus(conn, record.getReservationId(), "COMPLETED");
                }

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

    private String normalizeSpaceType(String input) {
        String v = input == null ? "" : input.trim().toUpperCase();
        if ("GROUND".equals(v) || "UNDERGROUND".equals(v)) {
            return v;
        }
        if ("地上".equals(input)) return "GROUND";
        if ("地下".equals(input)) return "UNDERGROUND";
        throw new ServiceException("车位类型不能为空");
    }

    private List<ParkingSpace> loadSpacesByLotAndType(Long lotId, String typeCode) throws SQLException {
        final int pageSize = 200;
        int pageNo = 1;
        List<ParkingSpace> all = new java.util.ArrayList<>();
        while (true) {
            List<ParkingSpace> page = parkingSpaceDao.search("", "", lotId, pageNo, pageSize);
            if (page == null || page.isEmpty()) break;
            all.addAll(page);
            if (page.size() < pageSize) break;
            pageNo++;
            if (pageNo > 200) break;
        }
        return all.stream()
                .filter(s -> typeCode.equalsIgnoreCase(s.getType()))
                .sorted(Comparator.comparingLong(s -> s.getSpaceId() == null ? Long.MAX_VALUE : s.getSpaceId()))
                .collect(Collectors.toList());
    }

    private List<Long> pickEntrySpaceCandidates(List<ParkingSpace> spaces, LocalDateTime now) {
        LocalTime nowTime = now.toLocalTime();
        List<Long> ids = new java.util.ArrayList<>();
        for (ParkingSpace s : spaces) {
            if (s.getSpaceId() == null) continue;
            if ("OCCUPIED".equalsIgnoreCase(s.getStatus())) continue;
            if (s.getShareStartTime() != null && nowTime.isBefore(s.getShareStartTime())) continue;
            if (s.getShareEndTime() != null && nowTime.isAfter(s.getShareEndTime())) continue;
            ids.add(s.getSpaceId());
        }
        return ids;
    }
}
