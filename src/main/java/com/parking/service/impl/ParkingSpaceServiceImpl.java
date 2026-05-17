package com.parking.service.impl;

import com.parking.dao.ParkingLotDao;
import com.parking.dao.ParkingSpaceDao;
import com.parking.dao.UserDao;
import com.parking.dao.impl.ParkingLotDaoImpl;
import com.parking.dao.impl.ParkingSpaceDaoImpl;
import com.parking.dao.impl.UserDaoImpl;
import com.parking.entity.ParkingLot;
import com.parking.entity.ParkingSpace;
import com.parking.entity.User;
import com.parking.service.ParkingSpaceService;
import com.parking.service.ServiceException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
//车位模块
public class ParkingSpaceServiceImpl implements ParkingSpaceService {
    private final ParkingSpaceDao parkingSpaceDao = new ParkingSpaceDaoImpl();
    private final ParkingLotDao parkingLotDao = new ParkingLotDaoImpl();
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public long addSpace(ParkingSpace space) throws SQLException {
        validateForSave(space, false);
        return parkingSpaceDao.insert(space);
    }

    @Override
    public void updateSpace(ParkingSpace space) throws SQLException {
        if (space == null || space.getSpaceId() == null) {
            throw new ServiceException("车位ID不能为空");
        }
        ParkingSpace existing = parkingSpaceDao.findById(space.getSpaceId());
        if (existing == null) {
            throw new ServiceException("未找到对应车位");
        }
        ParkingSpace merged = mergeForUpdate(existing, space);
        validateForSave(merged, true);
        if (parkingSpaceDao.update(merged) == 0) {
            throw new ServiceException("未找到对应车位");
        }
    }

    @Override
    public void removeSpace(Long spaceId) throws SQLException {
        if (spaceId == null) {
            throw new ServiceException("车位ID不能为空");
        }
        if (parkingSpaceDao.deleteById(spaceId) == 0) {
            throw new ServiceException("未找到对应车位");
        }
    }

    @Override
    public void updateSpaceField(Long spaceId, String fieldName, String fieldValue) throws SQLException {
        if (spaceId == null) {
            throw new ServiceException("车位ID不能为空");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new ServiceException("字段名不能为空");
        }
        ParkingSpace existing = parkingSpaceDao.findById(spaceId);
        if (existing == null) {
            throw new ServiceException("未找到对应车位");
        }
        String field = fieldName.trim().toLowerCase();
        Object value = fieldValue == null ? "" : fieldValue.trim();
        switch (field) {
            case "lot_id":
                long lotId = Long.parseLong(value.toString());
                ParkingLot lot = parkingLotDao.findById(lotId);
                if (lot == null) throw new ServiceException("停车场ID不存在");
                value = lotId;
                break;
            case "owner_id":
                long ownerId = Long.parseLong(value.toString());
                User u = userDao.findById(ownerId);
                if (u == null) throw new ServiceException("所有者ID不存在");
                if (!"OWNER".equalsIgnoreCase(u.getRole() == null ? "" : u.getRole().trim())) {
                    throw new ServiceException("用户不是车位所有者");
                }
                value = ownerId;
                break;
            case "space_number":
                if (value.toString().isEmpty()) throw new ServiceException("车位编号不能为空");
                break;
            case "type":
                if (value.toString().isEmpty()) throw new ServiceException("车位类型不能为空");
                if (!"GROUND".equalsIgnoreCase(value.toString()) && !"UNDERGROUND".equalsIgnoreCase(value.toString())) {
                    throw new ServiceException("类型只能为地上或地下");
                }
                break;
            case "status":
                if (value.toString().isEmpty()) throw new ServiceException("车位状态不能为空");
                break;
            case "share_start_time":
            case "share_end_time":
                if (!value.toString().isEmpty()) {
                    value = java.sql.Time.valueOf(java.time.LocalTime.parse(value.toString()));
                }
                break;
        }
        if (parkingSpaceDao.updateSpaceField(spaceId, fieldName, value) == 0) {
            throw new ServiceException("未找到对应车位");
        }
    }

    @Override
    public List<ParkingSpace> querySpaces(String keyword, String status, Long lotId, int pageNo, int pageSize) throws SQLException {
        return parkingSpaceDao.search(keyword, status, lotId, pageNo, pageSize);
    }

    @Override
    public List<ParkingSpace> queryMySpaces(Long ownerId, int pageNo, int pageSize) throws SQLException {
        if (ownerId == null) {
            throw new ServiceException("所有者ID不能为空");
        }
        return parkingSpaceDao.findByOwner(ownerId, pageNo, pageSize);
    }

    @Override
    public void updateMyShareWindow(Long spaceId, Long ownerId, LocalTime shareStart, LocalTime shareEnd) throws SQLException {
        if (spaceId == null || ownerId == null) {
            throw new ServiceException("车位ID和所有者ID不能为空");
        }
        if (shareStart == null || shareEnd == null || !shareStart.isBefore(shareEnd)) {
            throw new ServiceException("共享时间区间无效");
        }
        if (parkingSpaceDao.updateShareWindow(spaceId, ownerId, shareStart, shareEnd) == 0) {
            throw new ServiceException("未找到车位或无权限修改");
        }
    }

    @Override
    public List<ParkingSpace> queryAvailableSpaces(LocalDateTime reserveStart, LocalDateTime reserveEnd, int pageNo, int pageSize) throws SQLException {
        if (reserveStart == null || reserveEnd == null || !reserveStart.isBefore(reserveEnd)) {
            throw new ServiceException("预约时间区间无效");
        }
        return parkingSpaceDao.findAvailableByTime(reserveStart, reserveEnd, pageNo, pageSize);
    }

    private void validateForSave(ParkingSpace space, boolean update) {
        if (space == null) {
            throw new ServiceException("车位信息不能为空");
        }
        if (update && space.getSpaceId() == null) {
            throw new ServiceException("车位ID不能为空");
        }
        if (space.getLotId() == null) {
            throw new ServiceException("停车场ID不能为空");
        }
        if (space.getOwnerId() == null) {
            throw new ServiceException("所有者ID不能为空");
        }
        if (space.getSpaceNumber() == null || space.getSpaceNumber().isBlank()) {
            throw new ServiceException("车位编号不能为空");
        }
        if (space.getType() == null || space.getType().isBlank()) {
            throw new ServiceException("车位类型不能为空");
        }
        if (space.getStatus() == null || space.getStatus().isBlank()) {
            throw new ServiceException("车位状态不能为空");
        }
        if (space.getShareStartTime() == null || space.getShareEndTime() == null) {
            throw new ServiceException("共享开始时间和结束时间不能为空");
        }
        if (!space.getShareStartTime().isBefore(space.getShareEndTime())) {
            throw new ServiceException("共享时间必须为开始早于结束");
        }

        try {
            ParkingLot lot = parkingLotDao.findById(space.getLotId());
            if (lot == null) {
                throw new ServiceException("停车场ID不存在，请先创建停车场");
            }

            User owner = userDao.findById(space.getOwnerId());
            if (owner == null) {
                throw new ServiceException("所有者ID不存在，请先创建用户");
            }
            String role = owner.getRole() == null ? "" : owner.getRole().trim().toUpperCase();
            if (!"OWNER".equals(role)) {
                throw new ServiceException("所有者ID对应用户不是车位所有者");
            }
        } catch (SQLException ex) {
            throw new ServiceException("校验车位关联信息失败：" + ex.getMessage());
        }
    }

    private ParkingSpace mergeForUpdate(ParkingSpace oldValue, ParkingSpace patch) {
        ParkingSpace merged = new ParkingSpace();
        merged.setSpaceId(oldValue.getSpaceId());
        merged.setLotId(patch.getLotId() == null ? oldValue.getLotId() : patch.getLotId());
        merged.setOwnerId(patch.getOwnerId() == null ? oldValue.getOwnerId() : patch.getOwnerId());
        merged.setSpaceNumber(mergeText(oldValue.getSpaceNumber(), patch.getSpaceNumber()));
        merged.setType(mergeText(oldValue.getType(), patch.getType()));
        merged.setStatus(mergeText(oldValue.getStatus(), patch.getStatus()));
        merged.setShareStartTime(patch.getShareStartTime() == null ? oldValue.getShareStartTime() : patch.getShareStartTime());
        merged.setShareEndTime(patch.getShareEndTime() == null ? oldValue.getShareEndTime() : patch.getShareEndTime());
        return merged;
    }

    private String mergeText(String oldValue, String newValue) {
        if (newValue == null) return oldValue;
        String nv = newValue.trim();
        return nv.isEmpty() ? oldValue : nv;
    }
}
