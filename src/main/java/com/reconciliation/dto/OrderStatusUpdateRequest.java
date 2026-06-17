package com.reconciliation.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OrderStatusUpdateRequest {

    private String orderNo;

    private String status;

    private String paymentTransactionId;

    private BigDecimal paymentAmount;

    private String paymentCurrency;

    private BigDecimal paymentExchangeRate;

    private Integer paymentQuantity;

    private String remark;
}
