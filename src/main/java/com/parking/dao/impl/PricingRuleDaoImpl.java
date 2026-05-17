package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.PricingRuleDao;
import com.parking.entity.PricingRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PricingRuleDaoImpl implements PricingRuleDao {
    @Override
    public PricingRule findActiveBySpaceType(Connection conn, String spaceType) throws SQLException {
        String sql = """
                SELECT rule_id, rule_name, charge_type, unit_price, unit_time, fixed_price, applicable_space_type, status
                FROM PricingRules
                WHERE status = 1
                  AND applicable_space_type = ?
                ORDER BY rule_id DESC
                LIMIT 1
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, spaceType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRule(rs);
                }
            }
        }
        return null;
    }

    @Override
    public long insert(PricingRule rule) throws SQLException {
        String insertSql = """
                INSERT INTO PricingRules(rule_id, rule_name, charge_type, unit_price, unit_time, fixed_price, applicable_space_type, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DbUtil.getConnection()) {
            for (int i = 0; i < 5; i++) {
                long nextId = findReusableId(conn);
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, nextId);
                    fillRuleParams(ps, rule, 2);
                    ps.executeUpdate();
                    return nextId;
                } catch (SQLException ex) {
                    if (!isDuplicateKey(ex)) {
                        throw ex;
                    }
                }
            }
        }
        throw new SQLException("Insert pricing rule failed");
    }

    @Override
    public int update(PricingRule rule) throws SQLException {
        String sql = """
                UPDATE PricingRules
                SET rule_name = ?, charge_type = ?, unit_price = ?, unit_time = ?, fixed_price = ?, applicable_space_type = ?, status = ?
                WHERE rule_id = ?
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            fillRuleParams(ps, rule, 1);
            ps.setLong(8, rule.getRuleId());
            return ps.executeUpdate();
        }
    }

    @Override
    public int deleteById(Long ruleId) throws SQLException {
        String sql = "DELETE FROM PricingRules WHERE rule_id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ruleId);
            return ps.executeUpdate();
        }
    }

    @Override
    public int updateRuleField(Long ruleId, String fieldName, Object value) throws SQLException {
        if (fieldName == null || fieldName.isBlank()) {
            throw new SQLException("Field name is required");
        }
        String column;
        switch (fieldName.trim().toLowerCase()) {
            case "rule_name":
                column = "rule_name";
                break;
            case "charge_type":
                column = "charge_type";
                break;
            case "unit_price":
                column = "unit_price";
                break;
            case "unit_time":
                column = "unit_time";
                break;
            case "fixed_price":
                column = "fixed_price";
                break;
            case "applicable_space_type":
                column = "applicable_space_type";
                break;
            case "status":
                column = "status";
                break;
            default:
                throw new SQLException("Unsupported pricing rule field: " + fieldName);
        }
        String sql = "UPDATE PricingRules SET " + column + " = ? WHERE rule_id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, value);
            ps.setLong(2, ruleId);
            return ps.executeUpdate();
        }
    }

    @Override
    public PricingRule findById(Long ruleId) throws SQLException {
        String sql = """
                SELECT rule_id, rule_name, charge_type, unit_price, unit_time, fixed_price, applicable_space_type, status
                FROM PricingRules
                WHERE rule_id = ?
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ruleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRule(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<PricingRule> search(String keyword, String chargeType, Integer status, int pageNo, int pageSize) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT rule_id, rule_name, charge_type, unit_price, unit_time, fixed_price, applicable_space_type, status
                FROM PricingRules
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            String like = "%" + k + "%";
            sql.append(" AND (rule_name LIKE ? OR CAST(rule_id AS CHAR) LIKE ? ");
            params.add(like);
            params.add(like);
            if (k.matches("\\d+")) {
                sql.append(" OR rule_id = ? ");
                params.add(Long.parseLong(k));
            }
            sql.append(") ");
        }
        if (chargeType != null && !chargeType.isBlank()) {
            sql.append(" AND charge_type = ? ");
            params.add(chargeType);
        }
        if (status != null) {
            sql.append(" AND status = ? ");
            params.add(status);
        }

        sql.append(" ORDER BY rule_id DESC LIMIT ? OFFSET ? ");
        params.add(pageSize);
        params.add((pageNo - 1) * pageSize);

        List<PricingRule> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRule(rs));
                }
            }
        }
        return list;
    }

    private PricingRule mapRule(ResultSet rs) throws SQLException {
        PricingRule rule = new PricingRule();
        rule.setRuleId(rs.getLong("rule_id"));
        rule.setRuleName(rs.getString("rule_name"));
        rule.setChargeType(rs.getString("charge_type"));
        rule.setUnitPrice(rs.getBigDecimal("unit_price"));
        int unitTime = rs.getInt("unit_time");
        if (!rs.wasNull()) {
            rule.setUnitTime(unitTime);
        }
        rule.setFixedPrice(rs.getBigDecimal("fixed_price"));
        rule.setApplicableSpaceType(rs.getString("applicable_space_type"));
        rule.setStatus(rs.getInt("status"));
        return rule;
    }

    private void fillRuleParams(PreparedStatement ps, PricingRule rule, int startIndex) throws SQLException {
        ps.setString(startIndex, rule.getRuleName());
        ps.setString(startIndex + 1, rule.getChargeType());
        ps.setBigDecimal(startIndex + 2, rule.getUnitPrice());
        ps.setObject(startIndex + 3, rule.getUnitTime());
        ps.setBigDecimal(startIndex + 4, rule.getFixedPrice());
        ps.setString(startIndex + 5, rule.getApplicableSpaceType());
        ps.setObject(startIndex + 6, rule.getStatus() == null ? 1 : rule.getStatus());
    }

    private long findReusableId(Connection conn) throws SQLException {
        String sql = """
                SELECT MIN(t.candidate_id) AS next_id
                FROM (
                    SELECT 1 AS candidate_id
                    UNION ALL
                    SELECT rule_id + 1 AS candidate_id
                    FROM PricingRules
                ) t
                LEFT JOIN PricingRules r ON r.rule_id = t.candidate_id
                WHERE r.rule_id IS NULL
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                long id = rs.getLong("next_id");
                if (id > 0) {
                    return id;
                }
            }
        }
        return 1L;
    }

    private boolean isDuplicateKey(SQLException ex) {
        return "23000".equals(ex.getSQLState()) || ex.getErrorCode() == 1062;
    }
}
