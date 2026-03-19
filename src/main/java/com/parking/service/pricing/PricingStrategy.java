package com.parking.service.pricing;

import com.parking.entity.PricingRule;

import java.math.BigDecimal;

public interface PricingStrategy {
    BigDecimal calculate(long parkingMinutes, PricingRule rule);
}
