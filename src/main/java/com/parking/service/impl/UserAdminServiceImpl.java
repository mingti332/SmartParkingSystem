package com.parking.service.impl;

import com.parking.dao.UserDao;
import com.parking.dao.impl.UserDaoImpl;
import com.parking.entity.User;
import com.parking.service.ServiceException;
import com.parking.service.UserAdminService;

import java.sql.SQLException;
import java.util.List;

//用户管理模块
public class UserAdminServiceImpl implements UserAdminService {
    private static final long PROTECTED_ADMIN_ID = 1L;
    private final UserDao userDao = new UserDaoImpl();

    @Override
    public List<User> queryUsers(String keyword, String role, Integer status, int pageNo, int pageSize) throws SQLException {
        return userDao.search(keyword, role, status, pageNo, pageSize);
    }

    @Override
    public long createUser(User user) throws SQLException {
        if (user == null) {
            throw new ServiceException("用户信息不能为空");
        }
        //判断名字输入是否为空并去除名字两边空格
        String username = user.getUsername() == null ? "" : user.getUsername().trim();
        String password = user.getPassword() == null ? "" : user.getPassword().trim();
        String role = user.getRole() == null ? "" : user.getRole().trim().toUpperCase();
        String phone = user.getPhone() == null ? "" : user.getPhone().trim();

        if (username.isEmpty()) {
            throw new ServiceException("用户名不能为空");
        }
        if (password.isEmpty()) {
            throw new ServiceException("密码不能为空");
        }
        if (!"OWNER".equals(role) && !"CAR_OWNER".equals(role)) {
            throw new ServiceException("角色只能是车位所有者或车主");
        }
        if (!phone.isEmpty() && !phone.matches("\\d{11}")) {
            throw new ServiceException("手机号格式错误");
        }
        if (userDao.findByUsername(username) != null) {
            throw new ServiceException("用户名已存在");
        }

        user.setUsername(username);
        user.setPassword(password);
        user.setRole(role);
        user.setPhone(phone);
        user.setRealName(user.getRealName() == null ? "" : user.getRealName().trim());
        return userDao.insert(user);
    }

    @Override
    public void changeStatus(Long userId, Integer status) throws SQLException {
        if (userId == null || status == null) {
            throw new ServiceException("用户ID和状态不能为空");
        }
        //1为启用，0为禁用
        if (status != 0 && status != 1) {
            throw new ServiceException("状态只能为0或1");
        }
        if (userDao.updateStatus(userId, status) == 0) {
            throw new ServiceException("用户不存在");
        }
    }

    @Override
    public void resetPassword(Long userId, String newPassword) throws SQLException {
        if (userId == null || newPassword == null || newPassword.isBlank()) {
            throw new ServiceException("用户ID和新密码不能为空");
        }
        if (userDao.updatePassword(userId, newPassword) == 0) {
            throw new ServiceException("用户不存在");
        }
    }

    @Override
    public void deleteUser(Long userId) throws SQLException {
        if (userId == null) {
            throw new ServiceException("用户ID不能为空");
        }
        if (userId == PROTECTED_ADMIN_ID) {
            throw new ServiceException("管理员账号不可删除");
        }
        if (!userDao.existsById(userId)) {
            throw new ServiceException("用户不存在");
        }
        if (userDao.hasOwnedSpaces(userId)) {
            throw new ServiceException("该用户名下仍有车位，不能删除，请先转移或删除车位");
        }
        if (userDao.hasActiveParking(userId)) {
            throw new ServiceException("该用户当前有车辆在场内，不能删除，请先完成出场");
        }
        if (userDao.hasDependencies(userId)) {
            throw new ServiceException("该用户已有预约/停车/支付/收益等关联记录，不能删除（建议改为禁用）");
        }
        // 操作日志不属于业务绑定，删除用户前先清理该用户日志，避免外键约束阻塞删除
        userDao.clearOperationLogsByUserId(userId);
        if (userDao.deleteById(userId) == 0) {
            throw new ServiceException("删除失败，请重试");
        }
    }
}
