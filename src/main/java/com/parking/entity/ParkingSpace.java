package com.parking.entity;

import java.time.LocalTime;

public class ParkingSpace {
    private Long spaceId;
    private Long lotId;
    private Long ownerId;
    private String spaceNumber;
    private String type;
    private String status;
    private LocalTime shareStartTime;
    private LocalTime shareEndTime;

    public Long getSpaceId() {
        return spaceId;
    }

    public void setSpaceId(Long spaceId) {
        this.spaceId = spaceId;
    }

    public Long getLotId() {
        return lotId;
    }

    public void setLotId(Long lotId) {
        this.lotId = lotId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getSpaceNumber() {
        return spaceNumber;
    }

    public void setSpaceNumber(String spaceNumber) {
        this.spaceNumber = spaceNumber;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalTime getShareStartTime() {
        return shareStartTime;
    }

    public void setShareStartTime(LocalTime shareStartTime) {
        this.shareStartTime = shareStartTime;
    }

    public LocalTime getShareEndTime() {
        return shareEndTime;
    }

    public void setShareEndTime(LocalTime shareEndTime) {
        this.shareEndTime = shareEndTime;
    }
}
