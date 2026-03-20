package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.ParkingLotDao;
import com.parking.entity.ParkingLot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

public class ParkingLotDaoImpl implements ParkingLotDao {
    @Override
    public long insert(ParkingLot lot) throws SQLException {
        String insertSql = """
                INSERT INTO ParkingLots(lot_id, lot_name, address, total_spaces, open_time, close_time, description)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DbUtil.getConnection()) {
            for (int i = 0; i < 5; i++) {
                long nextId = findReusableId(conn);
                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setLong(1, nextId);
                    ps.setString(2, lot.getLotName());
                    ps.setString(3, lot.getAddress());
                    ps.setInt(4, lot.getTotalSpaces());
                    ps.setTime(5, lot.getOpenTime() == null ? null : Time.valueOf(lot.getOpenTime()));
                    ps.setTime(6, lot.getCloseTime() == null ? null : Time.valueOf(lot.getCloseTime()));
                    ps.setString(7, lot.getDescription());
                    ps.executeUpdate();
                    return nextId;
                } catch (SQLException ex) {
                    if (!isDuplicateKey(ex)) {
                        throw ex;
                    }
                }
            }
        }
        throw new SQLException("Insert parking lot failed");
    }

    @Override
    public int update(ParkingLot lot) throws SQLException {
        String sql = """
                UPDATE ParkingLots
                SET lot_name = ?, address = ?, total_spaces = ?, open_time = ?, close_time = ?, description = ?
                WHERE lot_id = ?
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, lot.getLotName());
            ps.setString(2, lot.getAddress());
            ps.setInt(3, lot.getTotalSpaces());
            ps.setTime(4, lot.getOpenTime() == null ? null : Time.valueOf(lot.getOpenTime()));
            ps.setTime(5, lot.getCloseTime() == null ? null : Time.valueOf(lot.getCloseTime()));
            ps.setString(6, lot.getDescription());
            ps.setLong(7, lot.getLotId());
            return ps.executeUpdate();
        }
    }

    @Override
    public int deleteById(Long lotId) throws SQLException {
        String sql = "DELETE FROM ParkingLots WHERE lot_id = ?";
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            return ps.executeUpdate();
        }
    }

    @Override
    public ParkingLot findById(Long lotId) throws SQLException {
        String sql = """
                SELECT lot_id, lot_name, address, total_spaces, open_time, close_time, description
                FROM ParkingLots
                WHERE lot_id = ?
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, lotId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    @Override
    public List<ParkingLot> search(String keyword, int pageNo, int pageSize) throws SQLException {
        int offset = (pageNo - 1) * pageSize;
        String sql = """
                SELECT lot_id, lot_name, address, total_spaces, open_time, close_time, description
                FROM ParkingLots
                WHERE lot_name LIKE ? OR address LIKE ?
                ORDER BY lot_id DESC
                LIMIT ? OFFSET ?
                """;
        List<ParkingLot> list = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            String kw = "%" + (keyword == null ? "" : keyword.trim()) + "%";
            ps.setString(1, kw);
            ps.setString(2, kw);
            ps.setInt(3, pageSize);
            ps.setInt(4, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    private long findReusableId(Connection conn) throws SQLException {
        String sql = """
                SELECT MIN(t.candidate_id) AS next_id
                FROM (
                    SELECT 1 AS candidate_id
                    UNION ALL
                    SELECT lot_id + 1 AS candidate_id
                    FROM ParkingLots
                ) t
                LEFT JOIN ParkingLots p ON p.lot_id = t.candidate_id
                WHERE p.lot_id IS NULL
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

    private ParkingLot mapRow(ResultSet rs) throws SQLException {
        ParkingLot lot = new ParkingLot();
        lot.setLotId(rs.getLong("lot_id"));
        lot.setLotName(rs.getString("lot_name"));
        lot.setAddress(rs.getString("address"));
        lot.setTotalSpaces(rs.getInt("total_spaces"));
        Time open = rs.getTime("open_time");
        if (open != null) {
            lot.setOpenTime(open.toLocalTime());
        }
        Time close = rs.getTime("close_time");
        if (close != null) {
            lot.setCloseTime(close.toLocalTime());
        }
        lot.setDescription(rs.getString("description"));
        return lot;
    }
}
