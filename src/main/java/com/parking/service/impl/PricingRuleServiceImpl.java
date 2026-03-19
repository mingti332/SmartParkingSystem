package com.parking.service.impl;

import com.parking.dao.PricingRuleDao;
import com.parking.dao.impl.PricingRuleDaoImpl;
import com.parking.entity.PricingRule;
import com.parking.service.PricingRuleService;
import com.parking.service.ServiceException;

import java.sql.SQLException;
import java.util.List;
//计费策略
public class PricingRuleServiceImpl implements PricingRuleService {
    private final PricingRuleDao pricingRuleDao = new PricingRuleDaoImpl();

    @Override
    public long addRule(PricingRule rule) throws SQLException {
        validate(rule, false);
        return pricingRuleDao.insert(rule);
    }

    @Override
    public void updateRule(PricingRule rule) throws SQLException {
        validate(rule, true);
        if (pricingRuleDao.update(rule) == 0) {
            throw new ServiceException("计费规则不存在");
        }
    }

    @Override
    public void removeRule(Long ruleId) throws SQLException {
        if (ruleId == null) {
            throw new ServiceException("规则ID不能为空");
        }
        if (pricingRuleDao.deleteById(ruleId) == 0) {
            throw new ServiceException("计费规则不存在");
        }
    }

    @Override
    public List<PricingRule> queryRules(String keyword, String chargeType, Integer status, int pageNo, int pageSize) throws SQLException {
        return pricingRuleDao.search(keyword, chargeType, status, pageNo, pageSize);
    }

    private void validate(PricingRule rule, boolean update) {
        if (rule == null) {
            throw new ServiceException("规则信息不能为空");
        }
        if (update && rule.getRuleId() == null) {
            throw new ServiceException("规则ID不能为空");
        }
        if (rule.getRuleName() == null || rule.getRuleName().isBlank()) {
            throw new ServiceException("规则名称不能为空");
        }
        if (rule.getChargeType() == null || rule.getChargeType().isBlank()) {
            throw new ServiceException("计费方式不能为空");
        }
        if ("HOURLY".equalsIgnoreCase(rule.getChargeType())) {
            if (rule.getUnitPrice() == null || rule.getUnitTime() == null || rule.getUnitTime() <= 0) {
                throw new ServiceException("按时计费需要填写单价和计费时长");
            }
            rule.setFixedPrice(null);
        } else if ("FIXED".equalsIgnoreCase(rule.getChargeType())) {
            if (rule.getFixedPrice() == null) {
                throw new ServiceException("按次计费需要填写固定价格");
            }
            rule.setUnitPrice(null);
            rule.setUnitTime(null);
        } else {
            throw new ServiceException("计费方式必须是 HOURLY 或 FIXED");
        }
        if (rule.getApplicableSpaceType() == null || rule.getApplicableSpaceType().isBlank()) {
            throw new ServiceException("适用车位类型不能为空");
        }
        if (rule.getStatus() == null) {
            rule.setStatus(1);
        }
    }
}
