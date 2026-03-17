package com.parking.service.impl;

import com.parking.dao.ParkingLotDao;
import com.parking.dao.impl.ParkingLotDaoImpl;
import com.parking.entity.ParkingLot;
import com.parking.service.ParkingLotService;
import com.parking.service.ServiceException;

import java.sql.SQLException;
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
        if (parkingLotDao.update(lot) == 0) {
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
        return parkingLotDao.search(keyword, pageNo, pageSize);
    }
}
