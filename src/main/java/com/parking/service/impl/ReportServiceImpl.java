package com.parking.service.impl;

import com.parking.dao.ReportDao;
import com.parking.dao.impl.ReportDaoImpl;
import com.parking.service.ReportService;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ReportServiceImpl implements ReportService {
    private final ReportDao reportDao = new ReportDaoImpl();

    @Override
    public List<Map<String, Object>> incomeByLot() throws SQLException {
        return reportDao.incomeByLot();
    }

    @Override
    public List<Map<String, Object>> reservationCountBySpace() throws SQLException {
        return reportDao.reservationCountBySpace();
    }

    @Override
    public List<Map<String, Object>> usageByHour() throws SQLException {
        return reportDao.usageByHour();
    }
}
