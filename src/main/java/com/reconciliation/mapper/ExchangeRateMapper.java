package com.reconciliation.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.reconciliation.entity.ExchangeRate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDate;

@Mapper
public interface ExchangeRateMapper extends BaseMapper<ExchangeRate> {

    @Select("SELECT rate FROM exchange_rate " +
            "WHERE source_currency = #{source} AND target_currency = #{target} " +
            "AND rate_date <= #{date} AND deleted = 0 " +
            "ORDER BY rate_date DESC LIMIT 1")
    BigDecimal findRate(@Param("source") String sourceCurrency,
                        @Param("target") String targetCurrency,
                        @Param("date") LocalDate rateDate);
}
