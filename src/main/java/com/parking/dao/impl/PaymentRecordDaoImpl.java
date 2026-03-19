package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.PaymentRecordDao;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaymentRecordDaoImpl implements PaymentRecordDao {
    @Override
    public long insertPaid(Connection conn, Long recordId, Long userId, BigDecimal amount, String method) throws SQLException {
        String sql = """
                INSERT INTO PaymentRecords(record_id, user_id, amount, payment_method, payment_time, payment_status)
                VALUES (?, ?, ?, ?, NOW(), 'PAID')
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, recordId);
            ps.setLong(2, userId);
            ps.setBigDecimal(3, amount);
            ps.setString(4, method);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("新增支付记录失败");
    }

    @Override
    public List<Map<String, Object>> findByUser(Long userId, String paymentStatus, int pageNo, int pageSize) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT payment_id, record_id, user_id, amount, payment_method, payment_time, payment_status
                FROM PaymentRecords
                WHERE user_id = ?
                """);
        List<Object> params = new ArrayList<>();
        params.add(userId);
        if (paymentStatus != null && !paymentStatus.isBlank()) {
            sql.append(" AND payment_status = ? ");
            params.add(paymentStatus);
        }
        sql.append(" ORDER BY payment_id DESC LIMIT ? OFFSET ? ");
        params.add(pageSize);
        params.add((pageNo - 1) * pageSize);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("payment_id", rs.getLong("payment_id"));
                    row.put("record_id", rs.getLong("record_id"));
                    row.put("user_id", rs.getLong("user_id"));
                    row.put("amount", rs.getBigDecimal("amount"));
                    row.put("payment_method", rs.getString("payment_method"));
                    row.put("payment_time", rs.getTimestamp("payment_time"));
                    row.put("payment_status", rs.getString("payment_status"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }
}
