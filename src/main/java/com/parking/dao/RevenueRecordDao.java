package com.parking.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface RevenueRecordDao {
    long insertUnsettled(Connection conn, Long ownerId, Long spaceId, Long paymentId, BigDecimal incomeAmount) throws SQLException;

    List<Map<String, Object>> findIncomeDetailByOwner(Long ownerId, int pageNo, int pageSize) throws SQLException;

    List<Map<String, Object>> searchForAdmin(String settleStatus, int pageNo, int pageSize) throws SQLException;

    BigDecimal sumIncomeByOwner(Long ownerId) throws SQLException;

    int settleById(Long revenueId) throws SQLException;
}
