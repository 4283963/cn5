package com.reconciliation.service;

import com.reconciliation.mapper.ExchangeRateMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateMapper exchangeRateMapper;

    @Value("${reconciliation.base-currency:CNY}")
    private String baseCurrency;

    private static final int INTERMEDIATE_SCALE = 6;

    public BigDecimal convertToBaseCurrency(BigDecimal amount, String currency, LocalDate rateDate) {
        if (currency.equals(baseCurrency)) {
            return amount;
        }
        BigDecimal rate = getRate(currency, baseCurrency, rateDate);
        if (rate == null) {
            log.warn("No exchange rate found for {}/{} on {}, using 1.0", currency, baseCurrency, rateDate);
            return amount;
        }
        return amount.multiply(rate).setScale(INTERMEDIATE_SCALE, RoundingMode.HALF_UP);
    }

    public BigDecimal getRate(String sourceCurrency, String targetCurrency, LocalDate rateDate) {
        return exchangeRateMapper.findRate(sourceCurrency, targetCurrency, rateDate);
    }
}
