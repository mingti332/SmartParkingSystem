package com.parking.service.impl;

import com.parking.dao.UserDao;
import com.parking.dao.impl.UserDaoImpl;
import com.parking.entity.User;
import com.parking.service.AuthService;
import com.parking.service.ServiceException;
import com.parking.util.PasswordEncoder;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthServiceImpl implements AuthService {
    private final UserDao userDao = new UserDaoImpl();
    private static final int MAX_FAIL_COUNT = 5;
    private static final long COOLDOWN_SECONDS = 60;

    private static final Map<String, LoginAttemptInfo> loginAttempts = new ConcurrentHashMap<>();

    public static class LoginAttemptInfo {
        private int failCount;
        private long lastFailTime;

        public LoginAttemptInfo() {
            this.failCount = 0;
            this.lastFailTime = 0;
        }

        public LoginAttemptInfo(int failCount, long lastFailTime) {
            this.failCount = failCount;
            this.lastFailTime = lastFailTime;
        }

        public int getFailCount() {
            return failCount;
        }

        public int getRemainingAttempts() {
            return MAX_FAIL_COUNT - failCount;
        }

        public long getLastFailTime() {
            return lastFailTime;
        }

        public int getRemainingCooldownSeconds() {
            if (failCount < MAX_FAIL_COUNT) return 0;
            long elapsed = (System.currentTimeMillis() - lastFailTime) / 1000;
            if (elapsed >= COOLDOWN_SECONDS) return 0;
            return (int) (COOLDOWN_SECONDS - elapsed);
        }

        public boolean isInCooldown() {
            return failCount >= MAX_FAIL_COUNT && getRemainingCooldownSeconds() > 0;
        }
    }

    @Override
    public User login(String username, String password) throws SQLException {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new ServiceException("Username and password are required");
        }
        String account = username.trim();
        String accountKey = account.toLowerCase();

        // Cooldown ended: clear stale lock state so new cycle starts from 0 failures.
        clearExpiredCooldown(accountKey);

        LoginAttemptInfo currentInfo = getLoginAttemptInfo(accountKey);
        if (currentInfo != null && currentInfo.isInCooldown()) {
            throw new ServiceException("Cooldown:" + currentInfo.getRemainingCooldownSeconds());
        }

        User user = userDao.findByUsername(account);
        if (user == null && account.matches("\\d+")) {
            user = userDao.findById(Long.parseLong(account));
        }
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new ServiceException("User does not exist or is disabled");
        }
        if (!PasswordEncoder.matches(password, user.getPassword())) {
            recordLoginFailure(accountKey);
            LoginAttemptInfo info = getLoginAttemptInfo(accountKey);
            if (info != null && info.isInCooldown()) {
                throw new ServiceException("Wrong password, locked for " + info.getRemainingCooldownSeconds() + " seconds");
            }
            throw new ServiceException("Wrong password, " + info.getRemainingAttempts() + " attempts remaining");
        }

        clearLoginAttempt(accountKey);
        return user;
    }

    public LoginAttemptInfo getLoginAttemptInfo(String account) {
        return loginAttempts.get(account.toLowerCase());
    }

    private void clearExpiredCooldown(String accountKey) {
        LoginAttemptInfo info = loginAttempts.get(accountKey);
        if (info != null && info.getFailCount() >= MAX_FAIL_COUNT && info.getRemainingCooldownSeconds() <= 0) {
            loginAttempts.remove(accountKey);
        }
    }

    private void recordLoginFailure(String accountKey) {
        LoginAttemptInfo info = loginAttempts.computeIfAbsent(accountKey, k -> new LoginAttemptInfo());
        info.failCount++;
        info.lastFailTime = System.currentTimeMillis();
    }

    public void clearLoginAttempt(String account) {
        loginAttempts.remove(account.toLowerCase());
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
        user.setPassword(PasswordEncoder.encode(user.getPassword().trim()));
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

}
