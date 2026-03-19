package com.parking.service.pricing;

import com.parking.entity.PricingRule;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class FixedPricingStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculate(long parkingMinutes, PricingRule rule) {
        return rule.getFixedPrice().setScale(2, RoundingMode.HALF_UP);
    }
}
