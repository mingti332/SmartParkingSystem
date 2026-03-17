package com.parking.config;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
//数据库连接配置
public final class DbUtil {
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = DbUtil.class.getClassLoader().getResourceAsStream("db.properties")) {
            if (in == null) {
                throw new IllegalStateException("未在类路径中找到 db.properties 配置文件");
            }
            PROPS.load(in);
            Class.forName(PROPS.getProperty("db.driver"));
        } catch (IOException | ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private DbUtil() {
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                PROPS.getProperty("db.url"),
                PROPS.getProperty("db.username"),
                PROPS.getProperty("db.password")
        );
    }
}
