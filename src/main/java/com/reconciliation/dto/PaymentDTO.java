package com.reconciliation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentDTO {

    private String transactionId;

    private String orderNo;

    private BigDecimal amount;

    private String currency;

    private BigDecimal exchangeRate;

    private Integer quantity;

    private String status;

    private LocalDateTime paymentTime;
}
