package com.parking.service.impl;

import com.parking.dao.RevenueRecordDao;
import com.parking.dao.impl.RevenueRecordDaoImpl;
import com.parking.service.RevenueService;
import com.parking.service.ServiceException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
//收益、结算、报表模块
public class RevenueServiceImpl implements RevenueService {
    private final RevenueRecordDao revenueRecordDao = new RevenueRecordDaoImpl();

    @Override
    public List<Map<String, Object>> getOwnerIncomeDetail(Long ownerId, int pageNo, int pageSize) throws SQLException {
        if (ownerId == null) {
            throw new ServiceException("所有者ID不能为空");
        }
        return revenueRecordDao.findIncomeDetailByOwner(ownerId, pageNo, pageSize);
    }

    @Override
    public List<Map<String, Object>> queryRevenueForAdmin(String settleStatus, int pageNo, int pageSize) throws SQLException {
        return revenueRecordDao.searchForAdmin(settleStatus, pageNo, pageSize);
    }

    @Override
    public BigDecimal getOwnerIncomeTotal(Long ownerId) throws SQLException {
        if (ownerId == null) {
            throw new ServiceException("所有者ID不能为空");
        }
        return revenueRecordDao.sumIncomeByOwner(ownerId);
    }

    @Override
    public void settleRevenue(Long revenueId) throws SQLException {
        if (revenueId == null) {
            throw new ServiceException("收益记录ID不能为空");
        }
        if (revenueRecordDao.settleById(revenueId) == 0) {
            throw new ServiceException("收益记录不存在或已结算");
        }
    }
}
