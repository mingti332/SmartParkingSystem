package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.UserDao;
import com.parking.entity.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDaoImpl implements UserDao {
    private static final long PROTECTED_ADMIN_ID = 1L;
    @Override
    public User findByUsername(String username) throws SQLException {
        String sql = """
                SELECT user_id, username, password, real_name, phone, role, status, create_time
                FROM Users
                WHERE username = ?
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getLong("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    user.setRealName(rs.getString("real_name"));
                    user.setPhone(rs.getString("phone"));
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getInt("status"));
                    Timestamp ts = rs.getTimestamp("create_time");
                    if (ts != null) {
                        user.setCreateTime(ts.toLocalDateTime());
                    }
                    return user;
                }
            }
        }
        return null;
    }

    @Override
    public long insert(User user) throws SQLException {
        String sql = """
                INSERT INTO Users(username, password, real_name, phone, role, status)
                VALUES (?, ?, ?, ?, ?, 1)
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getPassword());
            ps.setString(3, user.getRealName());
            ps.setString(4, user.getPhone());
            ps.setString(5, user.getRole());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("新增用户失败");
    }

    @Override
    public int updatePassword(Long userId, String newPassword) throws SQLException {
        String sql = "UPDATE Users SET password = ? WHERE user_id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newPassword);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        }
    }

    @Override
    public List<User> search(String keyword, String role, Integer status, int pageNo, int pageSize) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT user_id, username, password, real_name, phone, role, status, create_time
                FROM Users
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            String kw = "%" + k + "%";
            sql.append(" AND (CAST(user_id AS CHAR) LIKE ? OR username LIKE ? OR real_name LIKE ? ");
            params.add(kw);
            params.add(kw);
            params.add(kw);

            // Phone matching: only full 11 digits or the last 4 digits.
            if (k.matches("\\d{11}")) {
                sql.append(" OR phone = ? ");
                params.add(k);
            } else if (k.matches("\\d{4}")) {
                sql.append(" OR RIGHT(phone, 4) = ? ");
                params.add(k);
            }
            sql.append(") ");
        }

        if (role != null && !role.isBlank()) {
            sql.append(" AND role = ? ");
            params.add(role);
        }
        if (status != null) {
            sql.append(" AND status = ? ");
            params.add(status);
        }

        sql.append(" ORDER BY user_id DESC LIMIT ? OFFSET ? ");
        params.add(pageSize);
        params.add((pageNo - 1) * pageSize);

        List<User> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getLong("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setPassword(rs.getString("password"));
                    user.setRealName(rs.getString("real_name"));
                    user.setPhone(rs.getString("phone"));
                    user.setRole(rs.getString("role"));
                    user.setStatus(rs.getInt("status"));
                    Timestamp ts = rs.getTimestamp("create_time");
                    if (ts != null) {
                        user.setCreateTime(ts.toLocalDateTime());
                    }
                    list.add(user);
                }
            }
        }
        return list;
    }

    @Override
    public int updateStatus(Long userId, Integer status) throws SQLException {
        String sql = "UPDATE Users SET status = ? WHERE user_id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, status);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        }
    }

    @Override
    public boolean existsById(Long userId) throws SQLException {
        String sql = "SELECT 1 FROM Users WHERE user_id = ? LIMIT 1";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean hasOwnedSpaces(Long userId) throws SQLException {
        String sql = "SELECT 1 FROM ParkingSpaces WHERE owner_id = ? LIMIT 1";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean hasActiveParking(Long userId) throws SQLException {
        String sql = "SELECT 1 FROM ParkingRecords WHERE user_id = ? AND exit_time IS NULL LIMIT 1";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    @Override
    public boolean hasDependencies(Long userId) throws SQLException {
        String sql = """
                SELECT
                    (SELECT COUNT(1) FROM ParkingSpaces s WHERE s.owner_id = ?) AS c1,
                    (SELECT COUNT(1) FROM Reservations r WHERE r.user_id = ?) AS c2,
                    (SELECT COUNT(1) FROM ParkingRecords pr WHERE pr.user_id = ?) AS c3,
                    (SELECT COUNT(1) FROM PaymentRecords pay WHERE pay.user_id = ?) AS c4,
                    (SELECT COUNT(1) FROM RevenueRecords rev WHERE rev.owner_id = ?) AS c5,
                    (SELECT COUNT(1) FROM OperationLogs l WHERE l.user_id = ?) AS c6
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, userId);
            ps.setLong(3, userId);
            ps.setLong(4, userId);
            ps.setLong(5, userId);
            ps.setLong(6, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("c1") > 0
                            || rs.getLong("c2") > 0
                            || rs.getLong("c3") > 0
                            || rs.getLong("c4") > 0
                            || rs.getLong("c5") > 0
                            || rs.getLong("c6") > 0;
                }
            }
        }
        return false;
    }

    @Override
    public int deleteById(Long userId) throws SQLException {
        String sql = "DELETE FROM Users WHERE user_id = ? AND user_id <> ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, PROTECTED_ADMIN_ID);
            return ps.executeUpdate();
        }
    }
}
