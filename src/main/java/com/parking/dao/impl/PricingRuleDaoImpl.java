package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.PricingRuleDao;
import com.parking.entity.PricingRule;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
            }
        }
        return null;
    }

    @Override
    public long insert(PricingRule rule) throws SQLException {
        String sql = """
                INSERT INTO PricingRules(rule_name, charge_type, unit_price, unit_time, fixed_price, applicable_space_type, status)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            fillRuleParams(ps, rule);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("新增计费规则失败");
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
            fillRuleParams(ps, rule);
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
            // 查询规则：支持“规则名称模糊查询”；当关键字为纯数字时，额外支持按规则ID精确查询。
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

    private void fillRuleParams(PreparedStatement ps, PricingRule rule) throws SQLException {
        ps.setString(1, rule.getRuleName());
        ps.setString(2, rule.getChargeType());
        ps.setBigDecimal(3, rule.getUnitPrice());
        ps.setObject(4, rule.getUnitTime());
        ps.setBigDecimal(5, rule.getFixedPrice());
        ps.setString(6, rule.getApplicableSpaceType());
        ps.setObject(7, rule.getStatus() == null ? 1 : rule.getStatus());
    }
}
