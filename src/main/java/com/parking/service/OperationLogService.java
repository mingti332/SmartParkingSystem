package com.parking.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public interface OperationLogService {
    long addLog(Long userId, String operationType, String operationDesc) throws SQLException;

    List<Map<String, Object>> queryLogs(String keyword, int pageNo, int pageSize) throws SQLException;
}
