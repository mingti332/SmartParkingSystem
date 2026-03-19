package com.parking.service.impl;

import com.parking.dao.PaymentRecordDao;
import com.parking.dao.impl.PaymentRecordDaoImpl;
import com.parking.service.PaymentService;
import com.parking.service.ServiceException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class PaymentServiceImpl implements PaymentService {
    private final PaymentRecordDao paymentRecordDao = new PaymentRecordDaoImpl();

    @Override
    public List<Map<String, Object>> getMyPayments(Long userId, String paymentStatus, int pageNo, int pageSize) throws SQLException {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        return paymentRecordDao.findByUser(userId, paymentStatus, pageNo, pageSize);
    }
}
