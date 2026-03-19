package com.parking.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface PaymentRecordDao {
    long insertPaid(Connection conn, Long recordId, Long userId, BigDecimal amount, String method) throws SQLException;

    List<Map<String, Object>> findByUser(Long userId, String paymentStatus, int pageNo, int pageSize) throws SQLException;
}
