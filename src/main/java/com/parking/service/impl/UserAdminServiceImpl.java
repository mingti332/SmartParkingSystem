package com.parking.service.impl;

import com.parking.dao.UserDao;
import com.parking.dao.impl.UserDaoImpl;
import com.parking.entity.User;
import com.parking.service.ServiceException;
import com.parking.service.UserAdminService;
import com.parking.util.PasswordEncoder;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

public class UserAdminServiceImpl implements UserAdminService {
    private static final long PROTECTED_ADMIN_ID = 1L;
    private static final DateTimeFormatter USER_CREATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public List<User> queryUsers(String keyword, String role, Integer status, int pageNo, int pageSize) throws SQLException {
        return userDao.search(keyword, role, status, pageNo, pageSize);
    }

    @Override
    public long createUser(User user) throws SQLException {
        if (user == null) {
            throw new ServiceException("User info is required");
        }
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        String password = user.getPassword() == null ? "" : user.getPassword().trim();
        String role = user.getRole() == null ? "" : user.getRole().trim().toUpperCase();
        String phone = user.getPhone() == null ? "" : user.getPhone().trim();

        if (username.isEmpty()) {
            throw new ServiceException("Username is required");
        }
        if (password.isEmpty()) {
            throw new ServiceException("Password is required");
        }
        if (!"OWNER".equals(role) && !"CAR_OWNER".equals(role)) {
            throw new ServiceException("Role is required");
        }
        if (!phone.isEmpty() && !phone.matches("\\d{11}")) {
            throw new ServiceException("Phone must be 11 digits");
        }
        if (userDao.findByUsername(username) != null) {
            throw new ServiceException("Username already exists");
        }

        user.setUsername(username);
        user.setPassword(PasswordEncoder.encode(password));
        user.setRole(role);
        user.setPhone(phone);
        user.setRealName(user.getRealName() == null ? "" : user.getRealName().trim());
        return userDao.insert(user);
    }

    @Override
    public void changeStatus(Long userId, Integer status) throws SQLException {
        if (userId == null || status == null) {
            throw new ServiceException("userId and status are required");
        }
        if (status != 0 && status != 1) {
            throw new ServiceException("status must be 0 or 1");
        }
        if (userDao.updateStatus(userId, status) == 0) {
            throw new ServiceException("User not found");
        }
    }

    @Override
    public void resetPassword(Long userId, String newPassword) throws SQLException {
        if (userId == null || newPassword == null || newPassword.isBlank()) {
            throw new ServiceException("userId and newPassword are required");
        }
        String encodedNewPassword = PasswordEncoder.encode(newPassword.trim());
        if (userDao.updatePassword(userId, encodedNewPassword) == 0) {
            throw new ServiceException("User not found");
        }
    }

    @Override
    public void updateUserField(Long userId, String fieldName, String fieldValue) throws SQLException {
        if (userId == null) {
            throw new ServiceException("userId is required");
        }
        if (fieldName == null || fieldName.isBlank()) {
            throw new ServiceException("fieldName is required");
        }
        if (!userDao.existsById(userId)) {
            throw new ServiceException("User not found");
        }

        String field = fieldName.trim().toLowerCase();
        switch (field) {
            case "username": {
                String username = fieldValue == null ? "" : fieldValue.trim();
                if (username.isEmpty()) throw new ServiceException("Username is required");
                User existed = userDao.findByUsername(username);
                if (existed != null && existed.getUserId() != null && !existed.getUserId().equals(userId)) {
                    throw new ServiceException("Username already exists");
                }
                if (userDao.updateUserField(userId, "username", username) == 0) {
                    throw new ServiceException("User not found");
                }
                return;
            }
            case "real_name": {
                String realName = fieldValue == null ? "" : fieldValue.trim();
                if (userDao.updateUserField(userId, "real_name", realName) == 0) {
                    throw new ServiceException("User not found");
                }
                return;
            }
            case "phone": {
                String phone = fieldValue == null ? "" : fieldValue.trim();
                if (!phone.isEmpty() && !phone.matches("\\d{11}")) {
                    throw new ServiceException("Phone must be 11 digits");
                }
                if (userDao.updateUserField(userId, "phone", phone) == 0) {
                    throw new ServiceException("User not found");
                }
                return;
            }
            case "role": {
                String role = fieldValue == null ? "" : fieldValue.trim().toUpperCase();
                if (!"ADMIN".equals(role) && !"OWNER".equals(role) && !"CAR_OWNER".equals(role)) {
                    throw new ServiceException("Role is required");
                }
                if (userDao.updateUserField(userId, "role", role) == 0) {
                    throw new ServiceException("User not found");
                }
                return;
            }
            case "status": {
                String statusText = fieldValue == null ? "" : fieldValue.trim();
                if (!"0".equals(statusText) && !"1".equals(statusText)) {
                    throw new ServiceException("status must be 0 or 1");
                }
                int status = Integer.parseInt(statusText);
                if (userDao.updateUserField(userId, "status", status) == 0) {
                    throw new ServiceException("User not found");
                }
                return;
            }
            case "create_time": {
                String timeText = fieldValue == null ? "" : fieldValue.trim();
                if (timeText.isEmpty()) {
                    throw new ServiceException("create time is required");
                }
                LocalDateTime dt;
                try {
                    dt = LocalDateTime.parse(timeText, USER_CREATE_TIME_FMT);
                } catch (DateTimeParseException ex) {
                    throw new ServiceException("create time format must be yyyy-MM-dd HH:mm");
                }
                if (userDao.updateUserField(userId, "create_time", Timestamp.valueOf(dt)) == 0) {
                    throw new ServiceException("User not found");
                }
                return;
            }
            default:
                throw new ServiceException("Unsupported user field: " + fieldName);
        }
    }

    @Override
    public void deleteUser(Long userId) throws SQLException {
        if (userId == null) {
            throw new ServiceException("userId is required");
        }
        if (userId == PROTECTED_ADMIN_ID) {
            throw new ServiceException("Protected admin user cannot be deleted");
        }
        if (!userDao.existsById(userId)) {
            throw new ServiceException("User not found");
        }
        if (userDao.hasOwnedSpaces(userId)) {
            throw new ServiceException("User still owns parking spaces");
        }
        if (userDao.hasActiveParking(userId)) {
            throw new ServiceException("User has active parking records");
        }
        if (userDao.hasDependencies(userId)) {
            throw new ServiceException("User has related reservation/parking/payment/revenue data");
        }
        userDao.clearOperationLogsByUserId(userId);
        if (userDao.deleteById(userId) == 0) {
            throw new ServiceException("Delete user failed");
        }
    }
}
