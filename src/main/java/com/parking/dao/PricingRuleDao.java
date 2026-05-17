package com.parking.dao;

import com.parking.entity.PricingRule;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface PricingRuleDao {
    PricingRule findActiveBySpaceType(Connection conn, String spaceType) throws SQLException;

    long insert(PricingRule rule) throws SQLException;

    int update(PricingRule rule) throws SQLException;

    int deleteById(Long ruleId) throws SQLException;

    int updateRuleField(Long ruleId, String fieldName, Object value) throws SQLException;

    PricingRule findById(Long ruleId) throws SQLException;

    List<PricingRule> search(String keyword, String chargeType, Integer status, int pageNo, int pageSize) throws SQLException;
}
