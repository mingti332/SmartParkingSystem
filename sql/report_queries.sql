USE smart_parking;

-- 1) 按停车场统计总收入
SELECT l.lot_name, IFNULL(SUM(r.income_amount), 0) AS total_income
FROM ParkingLots l
LEFT JOIN ParkingSpaces s ON s.lot_id = l.lot_id
LEFT JOIN RevenueRecords r ON r.space_id = s.space_id
GROUP BY l.lot_id, l.lot_name
ORDER BY total_income DESC;

-- 2) 按车位统计预约次数
SELECT s.space_number, COUNT(r.reservation_id) AS reserve_count
FROM ParkingSpaces s
LEFT JOIN Reservations r ON r.space_id = s.space_id
GROUP BY s.space_id, s.space_number
ORDER BY reserve_count DESC;

-- 3) 按小时统计高峰时段
SELECT HOUR(entry_time) AS hour_slot, COUNT(*) AS usage_count
FROM ParkingRecords
GROUP BY HOUR(entry_time)
ORDER BY hour_slot;

-- 4) 停车位利用率（示例：按有停车记录的次数计算）
SELECT s.space_id,
       s.space_number,
       COUNT(p.record_id) AS used_times
FROM ParkingSpaces s
LEFT JOIN ParkingRecords p ON p.space_id = s.space_id
GROUP BY s.space_id, s.space_number
ORDER BY used_times DESC;
