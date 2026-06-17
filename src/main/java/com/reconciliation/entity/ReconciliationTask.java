package com.reconciliation.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("reconciliation_task")
public class ReconciliationTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate startDate;

    private LocalDate endDate;

    @TableField("status")
    private TaskStatus status;

    private Integer totalCount;

    private Integer matchCount;

    private Integer differenceCount;

    private BigDecimal totalOrderAmount;

    private BigDecimal totalPaymentAmount;

    private BigDecimal totalDifference;

    private LocalDateTime startedAt;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    public enum TaskStatus {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED
    }
}
