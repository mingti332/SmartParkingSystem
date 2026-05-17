package com.parking.service;

import com.parking.entity.User;

import java.sql.SQLException;
import java.util.List;

public interface UserAdminService {
    List<User> queryUsers(String keyword, String role, Integer status, int pageNo, int pageSize) throws SQLException;

    long createUser(User user) throws SQLException;

    void changeStatus(Long userId, Integer status) throws SQLException;

    void resetPassword(Long userId, String newPassword) throws SQLException;

    void updateUserField(Long userId, String fieldName, String fieldValue) throws SQLException;

    void deleteUser(Long userId) throws SQLException;
}
