package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.ParkingSpaceDao;
import com.parking.entity.ParkingSpace;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class ParkingSpaceDaoImpl implements ParkingSpaceDao {
    @Override
    public long insert(ParkingSpace space) throws SQLException {
        String insertSql = """
                INSERT INTO ParkingSpaces(space_id, lot_id, owner_id, space_number, type, status, share_start_time, share_end_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DbUtil.getConnection()) {
            for (int i = 0; i < 5; i++) {
                long nextId = findReusableId(conn);
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, nextId);
                    ps.setLong(2, space.getLotId());
                    ps.setLong(3, space.getOwnerId());
                    ps.setString(4, space.getSpaceNumber());
                    ps.setString(5, space.getType());
                    ps.setString(6, space.getStatus());
                    ps.setTime(7, space.getShareStartTime() == null ? null : Time.valueOf(space.getShareStartTime()));
                    ps.setTime(8, space.getShareEndTime() == null ? null : Time.valueOf(space.getShareEndTime()));
                    ps.executeUpdate();
                    return nextId;
                } catch (SQLException ex) {
                    if (!isDuplicateKey(ex)) {
                        throw ex;
                    }
                }
            }
        }
        throw new SQLException("Insert parking space failed");
    }

    @Override
    public int update(ParkingSpace space) throws SQLException {
        String sql = """
                UPDATE ParkingSpaces
                SET lot_id = ?, owner_id = ?, space_number = ?, type = ?, status = ?, share_start_time = ?, share_end_time = ?
                WHERE space_id = ?
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, space.getLotId());
            ps.setLong(2, space.getOwnerId());
            ps.setString(3, space.getSpaceNumber());
            ps.setString(4, space.getType());
            ps.setString(5, space.getStatus());
            ps.setTime(6, space.getShareStartTime() == null ? null : Time.valueOf(space.getShareStartTime()));
            ps.setTime(7, space.getShareEndTime() == null ? null : Time.valueOf(space.getShareEndTime()));
            ps.setLong(8, space.getSpaceId());
            return ps.executeUpdate();
        }
    }

    @Override
    public int deleteById(Long spaceId) throws SQLException {
        String sql = "DELETE FROM ParkingSpaces WHERE space_id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, spaceId);
            return ps.executeUpdate();
        }
    }

    @Override
    public List<ParkingSpace> search(String keyword, String status, Long lotId, int pageNo, int pageSize) throws SQLException {
        StringBuilder sql = new StringBuilder("""
                SELECT space_id, lot_id, owner_id, space_number, type, status, share_start_time, share_end_time
                FROM ParkingSpaces
                WHERE 1=1
                """);
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND space_number LIKE ? ");
            params.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            String s = status.trim();
            if ("NOT_RESERVED".equalsIgnoreCase(s)) {
                sql.append(" AND status <> 'RESERVED' ");
            } else {
                sql.append(" AND status = ? ");
                params.add(s);
            }
        }
        if (lotId != null) {
            sql.append(" AND lot_id = ? ");
            params.add(lotId);
        }
        sql.append(" ORDER BY space_id DESC LIMIT ? OFFSET ? ");
        int offset = (pageNo - 1) * pageSize;
        params.add(pageSize);
        params.add(offset);

        List<ParkingSpace> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public List<ParkingSpace> findByOwner(Long ownerId, int pageNo, int pageSize) throws SQLException {
        int offset = (pageNo - 1) * pageSize;
        String sql = """
                SELECT space_id, lot_id, owner_id, space_number, type, status, share_start_time, share_end_time
                FROM ParkingSpaces
                WHERE owner_id = ?
                ORDER BY space_id DESC
                LIMIT ? OFFSET ?
                """;
        List<ParkingSpace> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, ownerId);
            ps.setInt(2, pageSize);
            ps.setInt(3, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public int updateShareWindow(Long spaceId, Long ownerId, LocalTime shareStart, LocalTime shareEnd) throws SQLException {
        String sql = """
                UPDATE ParkingSpaces
                SET share_start_time = ?, share_end_time = ?
                WHERE space_id = ? AND owner_id = ?
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTime(1, shareStart == null ? null : Time.valueOf(shareStart));
            ps.setTime(2, shareEnd == null ? null : Time.valueOf(shareEnd));
            ps.setLong(3, spaceId);
            ps.setLong(4, ownerId);
            return ps.executeUpdate();
        }
    }

    @Override
    public List<ParkingSpace> findAvailableByTime(LocalDateTime reserveStart, LocalDateTime reserveEnd, int pageNo, int pageSize) throws SQLException {
        int offset = (pageNo - 1) * pageSize;
        String sql = """
                SELECT s.space_id, s.lot_id, s.owner_id, s.space_number, s.type, s.status, s.share_start_time, s.share_end_time
                FROM ParkingSpaces s
                WHERE s.status IN ('FREE', 'RESERVED')
                  AND TIME(?) >= IFNULL(s.share_start_time, '00:00:00')
                  AND TIME(?) <= IFNULL(s.share_end_time, '23:59:59')
                  AND NOT EXISTS (
                      SELECT 1
                      FROM Reservations r
                      WHERE r.space_id = s.space_id
                        AND r.status IN ('PENDING', 'ACTIVE')
                        AND ? < r.reserve_end
                        AND ? > r.reserve_start
                  )
                ORDER BY s.space_id DESC
                LIMIT ? OFFSET ?
                """;
        List<ParkingSpace> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(reserveStart));
            ps.setTimestamp(2, Timestamp.valueOf(reserveEnd));
            ps.setTimestamp(3, Timestamp.valueOf(reserveStart));
            ps.setTimestamp(4, Timestamp.valueOf(reserveEnd));
            ps.setInt(5, pageSize);
            ps.setInt(6, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    @Override
    public int updateStatus(Connection conn, Long spaceId, String status) throws SQLException {
        String sql = "UPDATE ParkingSpaces SET status = ? WHERE space_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, spaceId);
            return ps.executeUpdate();
        }
    }

    @Override
    public ParkingSpace findById(Connection conn, Long spaceId) throws SQLException {
        String sql = """
                SELECT space_id, lot_id, owner_id, space_number, type, status, share_start_time, share_end_time
                FROM ParkingSpaces
                WHERE space_id = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, spaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public SpaceMeta findMetaById(Connection conn, Long spaceId) throws SQLException {
        String sql = "SELECT type, owner_id FROM ParkingSpaces WHERE space_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, spaceId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new SpaceMeta(rs.getString("type"), rs.getLong("owner_id"));
                }
            }
        }
        return null;
    }

    private long findReusableId(Connection conn) throws SQLException {
        String sql = """
                SELECT MIN(t.candidate_id) AS next_id
                FROM (
                    SELECT 1 AS candidate_id
                    UNION ALL
                    SELECT space_id + 1 AS candidate_id
                    FROM ParkingSpaces
                ) t
                LEFT JOIN ParkingSpaces p ON p.space_id = t.candidate_id
                WHERE p.space_id IS NULL
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

    private ParkingSpace mapRow(ResultSet rs) throws SQLException {
        ParkingSpace s = new ParkingSpace();
        s.setSpaceId(rs.getLong("space_id"));
        s.setLotId(rs.getLong("lot_id"));
        s.setOwnerId(rs.getLong("owner_id"));
        s.setSpaceNumber(rs.getString("space_number"));
        s.setType(rs.getString("type"));
        s.setStatus(rs.getString("status"));
        Time shareStart = rs.getTime("share_start_time");
        if (shareStart != null) {
            s.setShareStartTime(shareStart.toLocalTime());
        }
        Time shareEnd = rs.getTime("share_end_time");
        if (shareEnd != null) {
            s.setShareEndTime(shareEnd.toLocalTime());
        }
        return s;
    }
}
