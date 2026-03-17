package com.parking.service.impl;

import com.parking.dao.ParkingSpaceDao;
import com.parking.dao.impl.ParkingSpaceDaoImpl;
import com.parking.entity.ParkingSpace;
import com.parking.service.ParkingSpaceService;
import com.parking.service.ServiceException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
//车位模块
public class ParkingSpaceServiceImpl implements ParkingSpaceService {
    private final ParkingSpaceDao parkingSpaceDao = new ParkingSpaceDaoImpl();

    @Override
    public long addSpace(ParkingSpace space) throws SQLException {
        validateForSave(space, false);
        return parkingSpaceDao.insert(space);
    }

    @Override
    public void updateSpace(ParkingSpace space) throws SQLException {
        validateForSave(space, true);
        if (parkingSpaceDao.update(space) == 0) {
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
    }
}
