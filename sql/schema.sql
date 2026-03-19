CREATE DATABASE IF NOT EXISTS smart_parking DEFAULT CHARACTER SET utf8mb4;
USE smart_parking;

CREATE TABLE IF NOT EXISTS Users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    real_name VARCHAR(50),
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ParkingLots (
    lot_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lot_name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    total_spaces INT NOT NULL,
    open_time TIME,
    close_time TIME,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS ParkingSpaces (
    space_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    lot_id BIGINT NOT NULL,
    owner_id BIGINT NOT NULL,
    space_number VARCHAR(30) NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'FREE',
    share_start_time TIME,
    share_end_time TIME,
    CONSTRAINT fk_spaces_lot FOREIGN KEY (lot_id) REFERENCES ParkingLots (lot_id),
    CONSTRAINT fk_spaces_owner FOREIGN KEY (owner_id) REFERENCES Users (user_id)
);

CREATE TABLE IF NOT EXISTS Reservations (
    reservation_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    reserve_start DATETIME NOT NULL,
    reserve_end DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancel_time DATETIME,
    CONSTRAINT fk_res_user FOREIGN KEY (user_id) REFERENCES Users (user_id),
    CONSTRAINT fk_res_space FOREIGN KEY (space_id) REFERENCES ParkingSpaces (space_id)
);

CREATE TABLE IF NOT EXISTS ParkingRecords (
    record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    reservation_id BIGINT,
    user_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    entry_time DATETIME NOT NULL,
    exit_time DATETIME,
    duration BIGINT,
    fee DECIMAL(10,2),
    CONSTRAINT fk_record_res FOREIGN KEY (reservation_id) REFERENCES Reservations (reservation_id),
    CONSTRAINT fk_record_user FOREIGN KEY (user_id) REFERENCES Users (user_id),
    CONSTRAINT fk_record_space FOREIGN KEY (space_id) REFERENCES ParkingSpaces (space_id)
);

CREATE TABLE IF NOT EXISTS PricingRules (
    rule_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    rule_name VARCHAR(100) NOT NULL,
    charge_type VARCHAR(20) NOT NULL,
    unit_price DECIMAL(10,2),
    unit_time INT,
    fixed_price DECIMAL(10,2),
    applicable_space_type VARCHAR(20),
    status TINYINT NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS PaymentRecords (
    payment_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    record_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    payment_method VARCHAR(20),
    payment_time DATETIME,
    payment_status VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    CONSTRAINT fk_pay_record FOREIGN KEY (record_id) REFERENCES ParkingRecords (record_id),
    CONSTRAINT fk_pay_user FOREIGN KEY (user_id) REFERENCES Users (user_id)
);

CREATE TABLE IF NOT EXISTS RevenueRecords (
    revenue_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_id BIGINT NOT NULL,
    space_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    income_amount DECIMAL(10,2) NOT NULL,
    settle_status VARCHAR(20) NOT NULL DEFAULT 'UNSETTLED',
    settle_time DATETIME,
    CONSTRAINT fk_rev_owner FOREIGN KEY (owner_id) REFERENCES Users (user_id),
    CONSTRAINT fk_rev_space FOREIGN KEY (space_id) REFERENCES ParkingSpaces (space_id),
    CONSTRAINT fk_rev_payment FOREIGN KEY (payment_id) REFERENCES PaymentRecords (payment_id)
);

CREATE TABLE IF NOT EXISTS OperationLogs (
    log_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    operation_type VARCHAR(50) NOT NULL,
    operation_desc VARCHAR(255),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES Users (user_id)
);

CREATE INDEX idx_spaces_lot_status ON ParkingSpaces (lot_id, status);
CREATE INDEX idx_res_space_time ON Reservations (space_id, reserve_start, reserve_end);
CREATE INDEX idx_res_user ON Reservations (user_id);
CREATE INDEX idx_pay_user_status ON PaymentRecords (user_id, payment_status);
CREATE INDEX idx_rev_owner_status ON RevenueRecords (owner_id, settle_status);
CREATE INDEX idx_log_user_time ON OperationLogs (user_id, create_time);

INSERT INTO Users (username, password, real_name, phone, role)
VALUES
('admin', '123456', '系统管理员', '13800000001', 'ADMIN'),
('owner01', 'owner123', '车位主1', '13800000002', 'OWNER'),
('driver01', 'driver123', '车主1', '13800000003', 'CAR_OWNER');

INSERT INTO ParkingLots (lot_name, address, total_spaces, open_time, close_time, description)
VALUES ('中心广场停车场', 'XX路100号', 200, '06:00:00', '23:00:00', '示例停车场');

INSERT INTO ParkingSpaces (lot_id, owner_id, space_number, type, status, share_start_time, share_end_time)
VALUES (1, 2, 'A-101', 'GROUND', 'FREE', '08:00:00', '22:00:00');

INSERT INTO PricingRules (rule_name, charge_type, unit_price, unit_time, fixed_price, applicable_space_type, status)
VALUES
('地上按时计费', 'HOURLY', 5.00, 60, NULL, 'GROUND', 1),
('地下按次计费', 'FIXED', NULL, NULL, 10.00, 'UNDERGROUND', 1);
