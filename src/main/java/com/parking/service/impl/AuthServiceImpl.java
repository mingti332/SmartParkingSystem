package com.parking.service.impl;

import com.parking.dao.UserDao;
import com.parking.dao.impl.UserDaoImpl;
import com.parking.entity.User;
import com.parking.service.AuthService;
import com.parking.service.ServiceException;

import java.sql.SQLException;

public class AuthServiceImpl implements AuthService {
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public User login(String username, String password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ServiceException("Username and password are required");
        }
        String account = username.trim();
        User user = userDao.findByUsername(account);
        if (user == null && account.matches("\\d+")) {
            user = userDao.findById(Long.parseLong(account));
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ServiceException("User does not exist or is disabled");
        }
        if (!password.equals(user.getPassword())) {
            throw new ServiceException("Wrong password");
        }
        return user;
    }

    @Override
    public long register(User user) throws SQLException {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            throw new ServiceException("Username is required");
        }
        user.setUsername(user.getUsername().trim());
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new ServiceException("Password is required");
        }
        user.setPassword(user.getPassword().trim());
        if (user.getRole() == null || user.getRole().isBlank()) {
            throw new ServiceException("Role is required");
        }
        if (user.getRealName() == null || user.getRealName().isBlank()) {
            throw new ServiceException("Real name is required");
        }
        user.setRealName(user.getRealName().trim());
        if (user.getPhone() == null || user.getPhone().isBlank()) {
            throw new ServiceException("Phone is required");
        }
        user.setPhone(user.getPhone().trim());
        if (!user.getPhone().matches("\\d{11}")) {
            throw new ServiceException("Phone must be 11 digits");
        }
        User existed = userDao.findByUsername(user.getUsername());
        if (existed != null) {
            throw new ServiceException("Username already exists: " + existed.getUsername() + " (id=" + existed.getUserId() + ")");
        }
        return userDao.insert(user);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) throws SQLException {
        if (userId == null) {
            throw new ServiceException("User id is required");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new ServiceException("New password is required");
        }
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new ServiceException("Old password is required");
        }
        if (userDao.updatePassword(userId, newPassword) == 0) {
            throw new ServiceException("User not found");
        }
    }
}
