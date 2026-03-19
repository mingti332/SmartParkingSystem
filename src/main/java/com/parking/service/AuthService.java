package com.parking.service;

import com.parking.entity.User;

import java.sql.SQLException;

public interface AuthService {
    User login(String username, String password) throws SQLException;

    long register(User user) throws SQLException;

    void changePassword(Long userId, String oldPassword, String newPassword) throws SQLException;
}
