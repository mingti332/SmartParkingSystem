package com.parking.service.impl;

import com.parking.dao.OperationLogDao;
import com.parking.dao.impl.OperationLogDaoImpl;
import com.parking.service.OperationLogService;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class OperationLogServiceImpl implements OperationLogService {
    private final OperationLogDao operationLogDao = new OperationLogDaoImpl();

    @Override
    public long addLog(Long userId, String operationType, String operationDesc) throws SQLException {
        String type = operationType == null ? "" : operationType.trim();
        if (type.isEmpty()) {
            throw new IllegalArgumentException("operationType is required");
        }
        String desc = operationDesc == null ? "" : operationDesc.trim();
        return operationLogDao.insert(userId, type, desc);
    }

    @Override
    public List<Map<String, Object>> queryLogs(String keyword, String category, int pageNo, int pageSize) throws SQLException {
        return operationLogDao.queryLogs(keyword, category, pageNo, pageSize);
    }

    @Override
    public int clearLogs(String category) throws SQLException {
        return operationLogDao.clearLogs(category);
    }
}
