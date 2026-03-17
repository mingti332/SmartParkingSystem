package com.parking.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface PaymentService {
    List<Map<String, Object>> getMyPayments(Long userId, String paymentStatus, int pageNo, int pageSize) throws SQLException;
}
