package com.parking.service.impl;

import com.parking.dao.UserDao;
import com.parking.dao.impl.UserDaoImpl;
import com.parking.entity.User;
import com.parking.service.AuthService;
import com.parking.service.ServiceException;

import java.sql.SQLException;


//登录、注册、改密
public class AuthServiceImpl implements AuthService {
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public User login(String username, String password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ServiceException("用户名和密码不能为空");
        }
        User user = userDao.findByUsername(username);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ServiceException("用户不存在或已被禁用");
        }
        if (!password.equals(user.getPassword())) {
            throw new ServiceException("密码错误");
        }
        return user;
    }

    @Override
    public long register(User user) throws SQLException {
        if (user == null || user.getUsername() == null || user.getUsername().isBlank()) {
            throw new ServiceException("用户名不能为空");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new ServiceException("密码不能为空");
        }
        if (user.getRole() == null || user.getRole().isBlank()) {
            throw new ServiceException("角色不能为空");
        }
        if (userDao.findByUsername(user.getUsername()) != null) {
            throw new ServiceException("用户名已存在");
        }
        return userDao.insert(user);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) throws SQLException {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        if (newPassword == null || newPassword.isBlank()) {
            throw new ServiceException("新密码不能为空");
        }
        // Minimal template implementation: oldPassword check can be expanded with current session context.
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new ServiceException("旧密码不能为空");
        }
        if (userDao.updatePassword(userId, newPassword) == 0) {
            throw new ServiceException("用户不存在");
        }
    }
}
