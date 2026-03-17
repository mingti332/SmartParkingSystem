package com.parking.dao.impl;

import com.parking.config.DbUtil;
import com.parking.dao.ReportDao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportDaoImpl implements ReportDao {
    @Override
    public List<Map<String, Object>> incomeByLot() throws SQLException {
        String sql = """
                SELECT l.lot_name, IFNULL(SUM(r.income_amount), 0) AS total_income
                FROM ParkingLots l
                LEFT JOIN ParkingSpaces s ON s.lot_id = l.lot_id
                LEFT JOIN RevenueRecords r ON r.space_id = s.space_id
                GROUP BY l.lot_id, l.lot_name
                ORDER BY total_income DESC
                """;
        return query(sql);
    }

    @Override
    public List<Map<String, Object>> reservationCountBySpace() throws SQLException {
        String sql = """
                SELECT s.space_id,
                       s.space_number,
                       l.lot_name,
                       s.type,
                       s.status,
                       (
                           SELECT COUNT(1)
                           FROM Reservations r
                           WHERE r.space_id = s.space_id
                             AND r.status <> 'CANCELED'
                       ) AS reserve_count
                FROM ParkingSpaces s
                LEFT JOIN ParkingLots l ON l.lot_id = s.lot_id
                ORDER BY reserve_count DESC, s.space_id DESC
                """;
        return query(sql);
    }

    @Override
    public List<Map<String, Object>> usageByHour() throws SQLException {
        String sql = """
                SELECT HOUR(entry_time) AS hour_slot, COUNT(*) AS usage_count
                FROM ParkingRecords
                GROUP BY HOUR(entry_time)
                ORDER BY hour_slot
                """;
        return query(sql);
    }

    private List<Map<String, Object>> query(String sql) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Connection conn = DbUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData metaData = rs.getMetaData();
            int count = metaData.getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                for (int i = 1; i <= count; i++) {
                    row.put(metaData.getColumnLabel(i), rs.getObject(i));
                }
                result.add(row);
            }
        }
        return result;
    }
}
