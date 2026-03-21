package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.OperationLogDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OperationLogDaoImpl implements OperationLogDao {
    @Override
    public long insert(Long userId, String operationType, String operationDesc) throws SQLException {
        String sql = """
                INSERT INTO OperationLogs(user_id, operation_type, operation_desc)
                VALUES (?, ?, ?)
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            if (userId == null) {
                ps.setNull(1, java.sql.Types.BIGINT);
            } else {
                ps.setLong(1, userId);
            }
            ps.setString(2, operationType);
            ps.setString(3, operationDesc);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        return 0L;
    }

    @Override
    public List<Map<String, Object>> queryLogs(String keyword, String category, int pageNo, int pageSize) throws SQLException {
        int safePageNo = Math.max(1, pageNo);
        int safePageSize = Math.max(1, pageSize);
        int offset = (safePageNo - 1) * safePageSize;

        String kw = keyword == null ? "" : keyword.trim();
        boolean hasKeyword = !kw.isEmpty();
        boolean numericKeyword = hasKeyword && kw.matches("\\d+");

        StringBuilder sql = new StringBuilder("""
                SELECT l.log_id,
                       l.user_id,
                       u.username,
                       l.operation_type,
                       l.operation_desc,
                       l.create_time
                FROM OperationLogs l
                LEFT JOIN Users u ON u.user_id = l.user_id
                """);
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        if (hasKeyword) {
            if (numericKeyword) {
                conditions.add("(l.user_id = ? OR l.log_id = ?)");
                long v = Long.parseLong(kw);
                params.add(v);
                params.add(v);
            } else {
                conditions.add("(u.username LIKE ? OR l.operation_type LIKE ? OR l.operation_desc LIKE ?)");
                String like = "%" + kw + "%";
                params.add(like);
                params.add(like);
                params.add(like);
            }
        }
        appendCategoryCondition(conditions, params, category, true);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        sql.append(" ORDER BY l.create_time DESC, l.log_id DESC LIMIT ? OFFSET ? ");
        params.add(safePageSize);
        params.add(offset);

        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData metaData = rs.getMetaData();
                int colCount = metaData.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(metaData.getColumnLabel(i), rs.getObject(i));
                    }
                    result.add(row);
                }
            }
        }
        return result;
    }

    @Override
    public int clearLogs(String category) throws SQLException {
        StringBuilder sql = new StringBuilder("DELETE FROM OperationLogs");
        List<Object> params = new ArrayList<>();
        List<String> conditions = new ArrayList<>();
        appendCategoryCondition(conditions, params, category, false);
        if (!conditions.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", conditions));
        }
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps.executeUpdate();
        }
    }

    private void appendCategoryCondition(List<String> conditions, List<Object> params, String category, boolean withAlias) {
        String cat = category == null ? "" : category.trim().toUpperCase();
        if (cat.isEmpty() || "ALL".equals(cat)) {
            return;
        }
        String typeCol = withAlias ? "l.operation_type" : "operation_type";
        String descCol = withAlias ? "l.operation_desc" : "operation_desc";

        switch (cat) {
            case "ADD":
                conditions.add("(" + typeCol + " LIKE ? OR " + descCol + " LIKE ?)");
                params.add("%新增%");
                params.add("%新增%");
                break;
            case "DELETE":
                conditions.add("(" + typeCol + " LIKE ? OR " + descCol + " LIKE ?)");
                params.add("%删除%");
                params.add("%删除%");
                break;
            case "UPDATE":
                conditions.add("(" + typeCol + " LIKE ? OR " + descCol + " LIKE ? OR " + descCol + " LIKE ? OR " + descCol + " LIKE ? OR " + descCol + " LIKE ? OR " + descCol + " LIKE ?)");
                params.add("%修改%");
                params.add("%修改%");
                params.add("%重置%");
                params.add("%禁用%");
                params.add("%启用%");
                params.add("%审核%");
                break;
            default:
                break;
        }
    }
}
