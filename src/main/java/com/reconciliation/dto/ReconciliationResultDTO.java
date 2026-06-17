package com.reconciliation.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ReconciliationResultDTO {

    private Long taskId;

    private String status;

    private Integer totalCount;

    private Integer matchCount;

    private Integer differenceCount;

    private BigDecimal totalOrderAmount;

    private BigDecimal totalPaymentAmount;

    private BigDecimal totalDifference;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    private List<ReconciliationDifference> differences;

    private AutoFixSummary autoFix;

    @Data
    public static class ReconciliationDifference {
        private String orderNo;
        private String paymentTransactionId;
        private String differenceType;
        private BigDecimal orderAmount;
        private String orderCurrency;
        private BigDecimal paymentAmount;
        private String paymentCurrency;
        private BigDecimal amountDifference;
        private BigDecimal exchangeRateUsed;
        private Integer quantityDifference;
        private String remark;
        private String autoFixStatus;
        private LocalDateTime autoFixedAt;
        private String autoFixRemark;
    }

    @Data
    public static class AutoFixSummary {
        private Integer candidateCount;
        private Integer autoFixedCount;
        private Integer failedCount;
        private Integer pendingCount;
    }
}
