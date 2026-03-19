package com.parking.service;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface RevenueService {
    List<Map<String, Object>> getOwnerIncomeDetail(Long ownerId, int pageNo, int pageSize) throws SQLException;

    List<Map<String, Object>> queryRevenueForAdmin(String settleStatus, int pageNo, int pageSize) throws SQLException;

    BigDecimal getOwnerIncomeTotal(Long ownerId) throws SQLException;

    void settleRevenue(Long revenueId) throws SQLException;
}
