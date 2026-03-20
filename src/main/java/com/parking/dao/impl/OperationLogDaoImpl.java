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
    public List<Map<String, Object>> queryLogs(String keyword, int pageNo, int pageSize) throws SQLException {
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
        if (hasKeyword) {
            sql.append(" WHERE ");
            if (numericKeyword) {
                sql.append(" (l.user_id = ? OR l.log_id = ?) ");
                long v = Long.parseLong(kw);
                params.add(v);
                params.add(v);
            } else {
                sql.append(" (u.username LIKE ? OR l.operation_type LIKE ? OR l.operation_desc LIKE ?) ");
                String like = "%" + kw + "%";
                params.add(like);
                params.add(like);
                params.add(like);
            }
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
}
