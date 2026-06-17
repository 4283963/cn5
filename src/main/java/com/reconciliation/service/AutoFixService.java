package com.reconciliation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reconciliation.client.OrderServiceClient;
import com.reconciliation.dto.OrderDTO;
import com.reconciliation.dto.OrderStatusUpdateRequest;
import com.reconciliation.dto.PaymentDTO;
import com.reconciliation.entity.ReconciliationDifference;
import com.reconciliation.entity.ReconciliationTask;
import com.reconciliation.mapper.ReconciliationDifferenceMapper;
import com.reconciliation.mapper.ReconciliationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutoFixService {

    private final OrderServiceClient orderServiceClient;
    private final ReconciliationDifferenceMapper differenceMapper;
    private final ReconciliationTaskMapper taskMapper;
    private final ExchangeRateService exchangeRateService;

    @Value("${reconciliation.auto-fix.enabled:true}")
    private boolean autoFixEnabled;

    @Value("${reconciliation.auto-fix.unpaid-status:UNPAID}")
    private String unpaidStatus;

    @Value("${reconciliation.auto-fix.paid-status:PAID}")
    private String paidStatus;

    @Value("${reconciliation.auto-fix.success-payment-status:SUCCESS}")
    private String successPaymentStatus;

    @Value("${reconciliation.auto-fix.skip-statuses:}")
    private List<String> skipStatuses;

    @Value("${reconciliation.amount-tolerance:0.01}")
    private BigDecimal amountTolerance;

    @Value("${reconciliation.base-currency:CNY}")
    private String baseCurrency;

    private static final int FINAL_SCALE = 2;

    @Transactional
    public int autoFixTask(Long taskId) {
        if (!autoFixEnabled) {
            log.info("Auto-fix is disabled, skipping for task {}", taskId);
            return 0;
        }

        ReconciliationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("对账任务不存在: " + taskId);
        }

        List<ReconciliationDifference> pendingDiffs = differenceMapper.selectList(
                new LambdaQueryWrapper<ReconciliationDifference>()
                        .eq(ReconciliationDifference::getTaskId, taskId)
                        .eq(ReconciliationDifference::getAutoFixStatus, ReconciliationDifference.AutoFixStatus.PENDING)
        );

        if (pendingDiffs.isEmpty()) {
            log.info("No pending auto-fix differences for task {}", taskId);
            return 0;
        }

        log.info("Found {} pending auto-fix differences for task {}", pendingDiffs.size(), taskId);
        int fixedCount = 0;

        for (ReconciliationDifference diff : pendingDiffs) {
            if (autoFixSingleDifference(diff, task.getStartDate())) {
                fixedCount++;
            }
        }

        log.info("Auto-fix completed for task {}, fixed {} out of {} differences", taskId, fixedCount, pendingDiffs.size());
        return fixedCount;
    }

    @Transactional
    public boolean autoFixSingleDifference(Long differenceId) {
        ReconciliationDifference diff = differenceMapper.selectById(differenceId);
        if (diff == null) {
            throw new RuntimeException("差异记录不存在: " + differenceId);
        }
        return autoFixSingleDifference(diff, null);
    }

    @Transactional
    public List<ReconciliationDifference> detectAndMarkAutoFixCandidates(
            Long taskId,
            List<OrderDTO> orders,
            List<PaymentDTO> payments,
            java.time.LocalDate rateDate) {

        Map<String, OrderDTO> orderMap = orders.stream()
                .collect(Collectors.toMap(OrderDTO::getOrderNo, o -> o, (a, b) -> b));
        Map<String, PaymentDTO> paymentMap = payments.stream()
                .collect(Collectors.toMap(PaymentDTO::getOrderNo, p -> p, (a, b) -> b));

        Set<String> skipStatusSet = Set.copyOf(skipStatuses);

        List<ReconciliationDifference> candidates = new java.util.ArrayList<>();

        for (OrderDTO order : orders) {
            if (skipStatusSet.contains(order.getStatus())) {
                continue;
            }

            PaymentDTO payment = paymentMap.get(order.getOrderNo());
            if (payment == null) {
                continue;
            }

            if (isAutoFixCandidate(order, payment, rateDate)) {
                ReconciliationDifference diff = new ReconciliationDifference();
                diff.setTaskId(taskId);
                diff.setOrderNo(order.getOrderNo());
                diff.setPaymentTransactionId(payment.getTransactionId());
                diff.setDifferenceType(ReconciliationDifference.DifferenceType.PAYMENT_SUCCESS_BUT_ORDER_UNPAID);
                diff.setOrderAmount(order.getAmount());
                diff.setOrderCurrency(order.getCurrency());
                diff.setPaymentAmount(payment.getAmount());
                diff.setPaymentCurrency(payment.getCurrency());

                BigDecimal orderInBase = exchangeRateService.convertToBaseCurrency(order.getAmount(), order.getCurrency(), rateDate);
                BigDecimal paymentInBase = exchangeRateService.convertToBaseCurrency(payment.getAmount(), payment.getCurrency(), rateDate);

                diff.setOrderAmountInBase(orderInBase.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
                diff.setPaymentAmountInBase(paymentInBase.setScale(FINAL_SCALE, RoundingMode.HALF_UP));
                diff.setAmountDifference(orderInBase.subtract(paymentInBase).abs().setScale(FINAL_SCALE, RoundingMode.HALF_UP));
                diff.setExchangeRateUsed(exchangeRateService.getRate(order.getCurrency(), baseCurrency, rateDate));
                diff.setOrderQuantity(order.getQuantity());
                diff.setPaymentQuantity(payment.getQuantity());
                diff.setRemark(String.format("支付网关已扣款(%s %s)但订单状态为%s，符合自动补账条件",
                        payment.getAmount(), payment.getCurrency(), order.getStatus()));
                diff.setAutoFixStatus(ReconciliationDifference.AutoFixStatus.PENDING);

                candidates.add(diff);
            }
        }

        log.info("Detected {} auto-fix candidates for task {}", candidates.size(), taskId);
        return candidates;
    }

    private boolean isAutoFixCandidate(OrderDTO order, PaymentDTO payment, java.time.LocalDate rateDate) {
        if (!unpaidStatus.equals(order.getStatus())) {
            return false;
        }

        if (!successPaymentStatus.equals(payment.getStatus())) {
            return false;
        }

        BigDecimal orderInBase = exchangeRateService.convertToBaseCurrency(order.getAmount(), order.getCurrency(), rateDate);
        BigDecimal paymentInBase = exchangeRateService.convertToBaseCurrency(payment.getAmount(), payment.getCurrency(), rateDate);
        BigDecimal amountDiff = orderInBase.subtract(paymentInBase).abs();

        if (amountDiff.compareTo(amountTolerance) > 0) {
            return false;
        }

        if (order.getQuantity() != null && payment.getQuantity() != null
                && !order.getQuantity().equals(payment.getQuantity())) {
            return false;
        }

        return true;
    }

    private boolean autoFixSingleDifference(ReconciliationDifference diff, java.time.LocalDate rateDate) {
        if (!ReconciliationDifference.AutoFixStatus.PENDING.equals(diff.getAutoFixStatus())) {
            log.info("Difference {} is not pending, auto-fix status: {}", diff.getId(), diff.getAutoFixStatus());
            return false;
        }

        try {
            OrderStatusUpdateRequest request = new OrderStatusUpdateRequest();
            request.setOrderNo(diff.getOrderNo());
            request.setStatus(paidStatus);
            request.setPaymentTransactionId(diff.getPaymentTransactionId());
            request.setPaymentAmount(diff.getPaymentAmount());
            request.setPaymentCurrency(diff.getPaymentCurrency());
            request.setPaymentExchangeRate(diff.getExchangeRateUsed());
            request.setPaymentQuantity(diff.getPaymentQuantity());
            request.setRemark("对账系统自动补账：检测到支付网关已成功扣款，自动更新订单为已支付状态");

            boolean success = orderServiceClient.updateOrderStatus(request);

            if (success) {
                diff.setAutoFixStatus(ReconciliationDifference.AutoFixStatus.AUTO_FIXED);
                diff.setAutoFixedAt(LocalDateTime.now());
                diff.setAutoFixRemark("自动补账成功，订单状态已更新为 " + paidStatus);
                differenceMapper.updateById(diff);
                log.info("Auto-fix succeeded for order {}", diff.getOrderNo());
                return true;
            } else {
                diff.setAutoFixStatus(ReconciliationDifference.AutoFixStatus.AUTO_FIX_FAILED);
                diff.setAutoFixRemark("订单服务返回更新失败");
                differenceMapper.updateById(diff);
                log.warn("Auto-fix failed for order {}: order service returned failure", diff.getOrderNo());
                return false;
            }

        } catch (Exception e) {
            log.error("Auto-fix failed for order {}", diff.getOrderNo(), e);
            diff.setAutoFixStatus(ReconciliationDifference.AutoFixStatus.AUTO_FIX_FAILED);
            diff.setAutoFixRemark("自动补账异常: " + e.getMessage());
            differenceMapper.updateById(diff);
            return false;
        }
    }

    public List<ReconciliationDifference> getPendingAutoFixDifferences(Long taskId) {
        return differenceMapper.selectList(
                new LambdaQueryWrapper<ReconciliationDifference>()
                        .eq(ReconciliationDifference::getTaskId, taskId)
                        .eq(ReconciliationDifference::getAutoFixStatus, ReconciliationDifference.AutoFixStatus.PENDING)
                        .orderByDesc(ReconciliationDifference::getCreatedAt)
        );
    }
}
