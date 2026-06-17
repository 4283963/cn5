package com.reconciliation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OrderDTO {

    private String orderNo;

    private BigDecimal amount;

    private String currency;

    private Integer quantity;

    private String skuCode;

    private String status;

    private LocalDateTime orderTime;
}
