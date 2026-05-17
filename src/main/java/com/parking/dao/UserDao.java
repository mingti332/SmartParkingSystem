package com.parking.dao;

import com.parking.entity.User;

import java.sql.SQLException;
import java.util.List;

public interface UserDao {
    User findByUsername(String username) throws SQLException;

    User findById(Long userId) throws SQLException;

    long insert(User user) throws SQLException;

    int updatePassword(Long userId, String newPassword) throws SQLException;

    List<User> search(String keyword, String role, Integer status, int pageNo, int pageSize) throws SQLException;

    int updateStatus(Long userId, Integer status) throws SQLException;

    int updateUserField(Long userId, String fieldName, Object value) throws SQLException;

    boolean existsById(Long userId) throws SQLException;

    boolean hasOwnedSpaces(Long userId) throws SQLException;

    boolean hasActiveParking(Long userId) throws SQLException;

    boolean hasDependencies(Long userId) throws SQLException;

    int clearOperationLogsByUserId(Long userId) throws SQLException;

    int deleteById(Long userId) throws SQLException;
}
