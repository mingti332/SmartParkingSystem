# 智能停车位共享与预约管理系统

基于 `JavaFX + JDBC + MySQL` 的课程设计项目。

## 1. 项目结构

```text
SmartParkingSystem
|- pom.xml
|- sql
|  |- schema.sql
|  |- report_queries.sql
|- docs
|  |- code-module-mapping.md
|- src/main
   |- java/com/parking
   |  |- app
   |  |- config
   |  |- entity
   |  |- dao
   |  |- service
   |- resources
      |- db.properties
```

## 2. 运行环境

- JDK 17 及以上
- Maven 3.8 及以上
- MySQL 5.5 及以上（建议 8.x）

## 3. 快速启动

1. 在 MySQL 中执行 `sql/schema.sql` 初始化数据库。
2. 修改 `src/main/resources/db.properties` 中的数据库连接信息。
3. 在项目根目录运行：

```bash
mvn clean javafx:run
```

## 4. 主要功能

- 用户与角色管理
- 停车场管理
- 车位管理
- 预约管理（含时段冲突检测）
- 停车入场/出场与自动计费
- 支付记录与收益结算
- 统计报表

## 5. 技术栈

- Java 17
- JavaFX 21
- Maven
- JDBC
- MySQL
- Git

## 6. 默认账号

- 管理员：`admin / 123456`
- 车位所有者：`owner01 / owner123`
- 车主：`driver01 / driver123`

## 7. 使用说明

- 项目文件统一使用 UTF-8 编码。
- 如出现乱码，请将相关文本文件重新保存为 UTF-8（建议无 BOM）。
