package com.parking.service.pricing;

import com.parking.entity.PricingRule;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class HourlyPricingStrategy implements PricingStrategy {
    @Override
    public BigDecimal calculate(long parkingMinutes, PricingRule rule) {
        int unitMinutes = rule.getUnitTime() == null ? 60 : rule.getUnitTime();
        long units = (long) Math.ceil((double) parkingMinutes / unitMinutes);
        if (units <= 0) {
            units = 1;
        }
        return rule.getUnitPrice().multiply(BigDecimal.valueOf(units)).setScale(2, RoundingMode.HALF_UP);
    }
}
