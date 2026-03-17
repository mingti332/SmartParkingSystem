package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.ParkingLotDao;
import com.parking.entity.ParkingLot;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ParkingLotDaoImpl implements ParkingLotDao {
    @Override
    public long insert(ParkingLot lot) throws SQLException {
        String sql = """
                INSERT INTO ParkingLots(lot_name, address, total_spaces, open_time, close_time, description)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, lot.getLotName());
            ps.setString(2, lot.getAddress());
            ps.setInt(3, lot.getTotalSpaces());
            ps.setTime(4, lot.getOpenTime() == null ? null : Time.valueOf(lot.getOpenTime()));
            ps.setTime(5, lot.getCloseTime() == null ? null : Time.valueOf(lot.getCloseTime()));
            ps.setString(6, lot.getDescription());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        }
        throw new SQLException("新增停车场失败");
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
