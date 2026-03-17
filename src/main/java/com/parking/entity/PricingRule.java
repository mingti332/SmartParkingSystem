package com.parking.entity;

import java.math.BigDecimal;

public class PricingRule {
    private Long ruleId;
    private String ruleName;
    private String chargeType;
    private BigDecimal unitPrice;
    private Integer unitTime;
    private BigDecimal fixedPrice;
    private String applicableSpaceType;
    private Integer status;

    public Long getRuleId() {
        return ruleId;
    }

    public void setRuleId(Long ruleId) {
        this.ruleId = ruleId;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getChargeType() {
        return chargeType;
    }

    public void setChargeType(String chargeType) {
        this.chargeType = chargeType;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    public Integer getUnitTime() {
        return unitTime;
    }

    public void setUnitTime(Integer unitTime) {
        this.unitTime = unitTime;
    }

    public BigDecimal getFixedPrice() {
        return fixedPrice;
    }

    public void setFixedPrice(BigDecimal fixedPrice) {
        this.fixedPrice = fixedPrice;
    }

    public String getApplicableSpaceType() {
        return applicableSpaceType;
    }

    public void setApplicableSpaceType(String applicableSpaceType) {
        this.applicableSpaceType = applicableSpaceType;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}
