package com.parking.service;

import com.parking.entity.PricingRule;

import java.sql.SQLException;
import java.util.List;

public interface PricingRuleService {
    long addRule(PricingRule rule) throws SQLException;

    void updateRule(PricingRule rule) throws SQLException;

    void removeRule(Long ruleId) throws SQLException;

    List<PricingRule> queryRules(String keyword, String chargeType, Integer status, int pageNo, int pageSize) throws SQLException;
}
