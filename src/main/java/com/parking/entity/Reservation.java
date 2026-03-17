package com.parking.entity;

import java.time.LocalDateTime;

public class Reservation {
    private Long reservationId;
    private Long userId;
    private Long spaceId;
    private LocalDateTime reserveStart;
    private LocalDateTime reserveEnd;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime cancelTime;

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public LocalDateTime getReserveStart() {
        return reserveStart;
    }

    public void setReserveStart(LocalDateTime reserveStart) {
        this.reserveStart = reserveStart;
    }

    public LocalDateTime getReserveEnd() {
        return reserveEnd;
    }

    public void setReserveEnd(LocalDateTime reserveEnd) {
        this.reserveEnd = reserveEnd;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getCancelTime() {
        return cancelTime;
    }

    public void setCancelTime(LocalDateTime cancelTime) {
        this.cancelTime = cancelTime;
    }
}
