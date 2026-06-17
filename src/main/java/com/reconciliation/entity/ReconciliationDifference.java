package com.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("reconciliation_difference")
public class ReconciliationDifference {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String orderNo;

    private String paymentTransactionId;

    @TableField("difference_type")
    private DifferenceType differenceType;

    private BigDecimal orderAmount;

    private String orderCurrency;

    private BigDecimal paymentAmount;

    private String paymentCurrency;

    private BigDecimal orderAmountInBase;

    private BigDecimal paymentAmountInBase;

    private BigDecimal amountDifference;

    private BigDecimal exchangeRateUsed;

    private Integer orderQuantity;

    private Integer paymentQuantity;

    private Integer quantityDifference;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableLogic
    private Integer deleted;

    public enum DifferenceType {
        AMOUNT_MISMATCH,
        QUANTITY_MISMATCH,
        ORDER_ONLY,
        PAYMENT_ONLY,
        EXCHANGE_RATE_MISMATCH
    }
}
