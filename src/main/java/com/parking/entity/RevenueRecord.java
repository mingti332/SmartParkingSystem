package com.parking.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RevenueRecord {
    private Long revenueId;
    private Long ownerId;
    private Long spaceId;
    private Long paymentId;
    private BigDecimal incomeAmount;
    private String settleStatus;
    private LocalDateTime settleTime;

    public Long getRevenueId() {
        return revenueId;
    }

    public void setRevenueId(Long revenueId) {
        this.revenueId = revenueId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(Long paymentId) {
        this.paymentId = paymentId;
    }

    public BigDecimal getIncomeAmount() {
        return incomeAmount;
    }

    public void setIncomeAmount(BigDecimal incomeAmount) {
        this.incomeAmount = incomeAmount;
    }

    public String getSettleStatus() {
        return settleStatus;
    }

    public void setSettleStatus(String settleStatus) {
        this.settleStatus = settleStatus;
    }

    public LocalDateTime getSettleTime() {
        return settleTime;
    }

    public void setSettleTime(LocalDateTime settleTime) {
        this.settleTime = settleTime;
    }
}
