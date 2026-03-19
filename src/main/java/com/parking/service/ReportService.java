package com.parking.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface ReportService {
    List<Map<String, Object>> incomeByLot() throws SQLException;

    List<Map<String, Object>> reservationCountBySpace() throws SQLException;

    List<Map<String, Object>> usageByHour() throws SQLException;
}
