package com.parking.service.impl;

import com.parking.dao.ParkingLotDao;
import com.parking.dao.impl.ParkingLotDaoImpl;
import com.parking.entity.ParkingLot;
import com.parking.service.ParkingLotService;
import com.parking.service.ServiceException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
//停车场模块
public class ParkingLotServiceImpl implements ParkingLotService {
    private final ParkingLotDao parkingLotDao = new ParkingLotDaoImpl();

    @Override
    public long addLot(ParkingLot lot) throws SQLException {
        if (lot == null || lot.getLotName() == null || lot.getLotName().isBlank()) {
            throw new ServiceException("停车场名称不能为空");
        }
        if (lot.getAddress() == null || lot.getAddress().isBlank()) {
            throw new ServiceException("停车场地址不能为空");
        } 
        if (lot.getTotalSpaces() == null || lot.getTotalSpaces() <= 0) {
            throw new ServiceException("总车位数必须大于0");
        }
        return parkingLotDao.insert(lot);
    }

    @Override
    public void updateLot(ParkingLot lot) throws SQLException {
        if (lot == null || lot.getLotId() == null) {
            throw new ServiceException("修改时停车场ID不能为空");
        }
        ParkingLot existing = parkingLotDao.findById(lot.getLotId());
        if (existing == null) {
            throw new ServiceException("未找到对应停车场");
        }

        ParkingLot merged = new ParkingLot();
        merged.setLotId(existing.getLotId());
        merged.setLotName(mergeText(existing.getLotName(), lot.getLotName()));
        merged.setAddress(mergeText(existing.getAddress(), lot.getAddress()));
        merged.setTotalSpaces(lot.getTotalSpaces() == null ? existing.getTotalSpaces() : lot.getTotalSpaces());
        merged.setOpenTime(lot.getOpenTime() == null ? existing.getOpenTime() : lot.getOpenTime());
        merged.setCloseTime(lot.getCloseTime() == null ? existing.getCloseTime() : lot.getCloseTime());
        merged.setDescription(mergeText(existing.getDescription(), lot.getDescription()));

        if (merged.getLotName() == null || merged.getLotName().isBlank()) {
            throw new ServiceException("停车场名称不能为空");
        }
        if (merged.getAddress() == null || merged.getAddress().isBlank()) {
            throw new ServiceException("停车场地址不能为空");
        }
        if (merged.getTotalSpaces() == null || merged.getTotalSpaces() <= 0) {
            throw new ServiceException("总车位数必须大于0");
        }
        if (parkingLotDao.update(merged) == 0) {
            throw new ServiceException("未找到对应停车场");
        }
    }

    @Override
    public void removeLot(Long lotId) throws SQLException {
        if (lotId == null) {
            throw new ServiceException("停车场ID不能为空");
        }
        if (parkingLotDao.deleteById(lotId) == 0) {
            throw new ServiceException("未找到对应停车场");
        }
    }

    @Override
    public List<ParkingLot> queryLots(String keyword, int pageNo, int pageSize) throws SQLException {
        String kw = keyword == null ? "" : keyword.trim();
        if (!kw.isEmpty() && kw.matches("\\d+")) {
            ParkingLot one = parkingLotDao.findById(Long.parseLong(kw));
            List<ParkingLot> list = new ArrayList<>();
            if (one != null) {
                list.add(one);
            }
            return list;
        }
        return parkingLotDao.search(keyword, pageNo, pageSize);
    }

    private String mergeText(String oldValue, String newValue) {
        if (newValue == null) return oldValue;
        String nv = newValue.trim();
        return nv.isEmpty() ? oldValue : nv;
    }
}
