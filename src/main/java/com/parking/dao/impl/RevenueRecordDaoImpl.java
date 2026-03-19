package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.RevenueRecordDao;

import java.math.BigDecimal;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RevenueRecordDaoImpl implements RevenueRecordDao {
    @Override
    public long insertUnsettled(Connection conn, Long ownerId, Long spaceId, Long paymentId, BigDecimal incomeAmount) throws SQLException {
        String sql = """
                INSERT INTO RevenueRecords(owner_id, space_id, payment_id, income_amount, settle_status)
                VALUES (?, ?, ?, ?, 'UNSETTLED')
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, ownerId);
            ps.setLong(2, spaceId);
            ps.setLong(3, paymentId);
            ps.setBigDecimal(4, incomeAmount);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("新增收益记录失败");
    }

    @Override
    public List<Map<String, Object>> findIncomeDetailByOwner(Long ownerId, int pageNo, int pageSize) throws SQLException {
        int offset = (pageNo - 1) * pageSize;
        String sql = """
                SELECT r.revenue_id, r.space_id, r.payment_id, r.income_amount, r.settle_status, r.settle_time, p.payment_time
                FROM RevenueRecords r
                LEFT JOIN PaymentRecords p ON p.payment_id = r.payment_id
                WHERE r.owner_id = ?
                ORDER BY r.revenue_id DESC
                LIMIT ? OFFSET ?
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerId);
            ps.setInt(2, pageSize);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("revenue_id", rs.getLong("revenue_id"));
                    row.put("space_id", rs.getLong("space_id"));
                    row.put("payment_id", rs.getLong("payment_id"));
                    row.put("income_amount", rs.getBigDecimal("income_amount"));
                    row.put("settle_status", rs.getString("settle_status"));
                    row.put("settle_time", rs.getTimestamp("settle_time"));
                    row.put("payment_time", rs.getTimestamp("payment_time"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Override
    public List<Map<String, Object>> searchForAdmin(String settleStatus, int pageNo, int pageSize) throws SQLException {
        int offset = (pageNo - 1) * pageSize;
        StringBuilder sql = new StringBuilder("""
                SELECT r.revenue_id,
                       r.owner_id,
                       p.user_id AS payer_user_id,
                       r.space_id,
                       r.payment_id,
                       r.income_amount,
                       r.settle_status,
                       r.settle_time
                FROM RevenueRecords r
                LEFT JOIN PaymentRecords p ON p.payment_id = r.payment_id
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();
        if (settleStatus != null && !settleStatus.isBlank()) {
            sql.append(" AND settle_status = ? ");
            params.add(settleStatus);
        }
        sql.append(" ORDER BY revenue_id DESC LIMIT ? OFFSET ? ");
        params.add(pageSize);
        params.add(offset);

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("revenue_id", rs.getLong("revenue_id"));
                    row.put("owner_id", rs.getLong("owner_id"));
                    // 支付人ID来自支付记录表 user_id，用于页面直接展示“支付人ID”。
                    row.put("payer_user_id", rs.getLong("payer_user_id"));
                    row.put("space_id", rs.getLong("space_id"));
                    row.put("payment_id", rs.getLong("payment_id"));
                    row.put("income_amount", rs.getBigDecimal("income_amount"));
                    row.put("settle_status", rs.getString("settle_status"));
                    row.put("settle_time", rs.getTimestamp("settle_time"));
                    rows.add(row);
                }
            }
        }
        return rows;
    }

    @Override
    public BigDecimal sumIncomeByOwner(Long ownerId) throws SQLException {
        String sql = "SELECT IFNULL(SUM(income_amount), 0) FROM RevenueRecords WHERE owner_id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigDecimal v = rs.getBigDecimal(1);
                    return v == null ? BigDecimal.ZERO : v;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    @Override
    public int settleById(Long revenueId) throws SQLException {
        String sql = """
                UPDATE RevenueRecords
                SET settle_status = 'SETTLED', settle_time = NOW()
                WHERE revenue_id = ? AND settle_status = 'UNSETTLED'
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, revenueId);
            return ps.executeUpdate();
        }
    }
}
