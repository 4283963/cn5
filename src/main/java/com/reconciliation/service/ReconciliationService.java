package com.reconciliation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.reconciliation.client.OrderServiceClient;
import com.reconciliation.client.PaymentGatewayClient;
import com.reconciliation.dto.OrderDTO;
import com.reconciliation.dto.PaymentDTO;
import com.reconciliation.dto.ReconciliationResultDTO;
import com.reconciliation.entity.ReconciliationDifference;
import com.reconciliation.entity.ReconciliationTask;
import com.reconciliation.mapper.ExchangeRateMapper;
import com.reconciliation.mapper.ReconciliationDifferenceMapper;
import com.reconciliation.mapper.ReconciliationTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final OrderServiceClient orderServiceClient;
    private final PaymentGatewayClient paymentGatewayClient;
    private final ReconciliationTaskMapper taskMapper;
    private final ReconciliationDifferenceMapper differenceMapper;
    private final ExchangeRateMapper exchangeRateMapper;

    @Value("${reconciliation.amount-tolerance:0.01}")
    private BigDecimal amountTolerance;

    @Value("${reconciliation.base-currency:CNY}")
    private String baseCurrency;

    @Transactional
    public ReconciliationResultDTO reconcile(LocalDate startDate, LocalDate endDate) {
        log.info("Starting reconciliation for period: {} to {}", startDate, endDate);

        ReconciliationTask task = createTask(startDate, endDate);

        try {
            task.setStatus(ReconciliationTask.TaskStatus.RUNNING);
            task.setStartedAt(LocalDateTime.now());
            taskMapper.updateById(task);

            List<OrderDTO> orders = orderServiceClient.fetchOrders(startDate, endDate);
            List<PaymentDTO> payments = paymentGatewayClient.fetchPayments(startDate, endDate);

            log.info("Fetched {} orders and {} payments", orders.size(), payments.size());

            Map<String, PaymentDTO> paymentByOrderNo = payments.stream()
                    .collect(Collectors.toMap(PaymentDTO::getOrderNo, p -> p, (a, b) -> b));

            Map<String, OrderDTO> orderByOrderNo = orders.stream()
                    .collect(Collectors.toMap(OrderDTO::getOrderNo, o -> o, (a, b) -> b));

            List<ReconciliationDifference> differences = new ArrayList<>();
            BigDecimal totalOrderAmount = BigDecimal.ZERO;
            BigDecimal totalPaymentAmount = BigDecimal.ZERO;
            int matchCount = 0;

            for (OrderDTO order : orders) {
                PaymentDTO payment = paymentByOrderNo.get(order.getOrderNo());
                totalOrderAmount = totalOrderAmount.add(convertToBaseCurrency(order.getAmount(), order.getCurrency(), startDate));

                if (payment == null) {
                    differences.add(buildDifference(task.getId(), order, null,
                            ReconciliationDifference.DifferenceType.ORDER_ONLY,
                            "订单在支付网关无对应记录"));
                    continue;
                }

                totalPaymentAmount = totalPaymentAmount.add(convertToBaseCurrency(payment.getAmount(), payment.getCurrency(), startDate));

                List<ReconciliationDifference> orderDiffs = compareOrderAndPayment(task.getId(), order, payment, startDate);
                if (orderDiffs.isEmpty()) {
                    matchCount++;
                } else {
                    differences.addAll(orderDiffs);
                }
            }

            for (PaymentDTO payment : payments) {
                if (!orderByOrderNo.containsKey(payment.getOrderNo())) {
                    totalPaymentAmount = totalPaymentAmount.add(convertToBaseCurrency(payment.getAmount(), payment.getCurrency(), startDate));
                    differences.add(buildDifferenceForPaymentOnly(task.getId(), payment,
                            "支付记录在订单系统无对应记录"));
                }
            }

            for (ReconciliationDifference diff : differences) {
                differenceMapper.insert(diff);
            }

            updateTaskCompletion(task, orders.size(), matchCount, differences.size(),
                    totalOrderAmount, totalPaymentAmount);

            return buildResult(task, differences);

        } catch (Exception e) {
            log.error("Reconciliation failed", e);
            task.setStatus(ReconciliationTask.TaskStatus.FAILED);
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            throw new RuntimeException("对账执行失败: " + e.getMessage(), e);
        }
    }

    private List<ReconciliationDifference> compareOrderAndPayment(Long taskId, OrderDTO order, PaymentDTO payment, LocalDate rateDate) {
        List<ReconciliationDifference> diffs = new ArrayList<>();

        BigDecimal orderAmountInBase = convertToBaseCurrency(order.getAmount(), order.getCurrency(), rateDate);
        BigDecimal paymentAmountInBase = convertToBaseCurrency(payment.getAmount(), payment.getCurrency(), rateDate);
        BigDecimal amountDiff = orderAmountInBase.subtract(paymentAmountInBase).abs();

        if (amountDiff.compareTo(amountTolerance) > 0) {
            ReconciliationDifference diff = new ReconciliationDifference();
            diff.setTaskId(taskId);
            diff.setOrderNo(order.getOrderNo());
            diff.setPaymentTransactionId(payment.getTransactionId());
            diff.setDifferenceType(ReconciliationDifference.DifferenceType.AMOUNT_MISMATCH);
            diff.setOrderAmount(order.getAmount());
            diff.setOrderCurrency(order.getCurrency());
            diff.setPaymentAmount(payment.getAmount());
            diff.setPaymentCurrency(payment.getCurrency());
            diff.setOrderAmountInBase(orderAmountInBase);
            diff.setPaymentAmountInBase(paymentAmountInBase);
            diff.setAmountDifference(amountDiff);

            BigDecimal rate = getExchangeRate(order.getCurrency(), baseCurrency, rateDate);
            diff.setExchangeRateUsed(rate);

            diff.setRemark(String.format("金额不一致: 订单 %s %s, 支付 %s %s, 基币差额 %s",
                    order.getAmount(), order.getCurrency(),
                    payment.getAmount(), payment.getCurrency(),
                    amountDiff));
            diffs.add(diff);
        }

        if (order.getQuantity() != null && payment.getQuantity() != null
                && !order.getQuantity().equals(payment.getQuantity())) {
            ReconciliationDifference diff = new ReconciliationDifference();
            diff.setTaskId(taskId);
            diff.setOrderNo(order.getOrderNo());
            diff.setPaymentTransactionId(payment.getTransactionId());
            diff.setDifferenceType(ReconciliationDifference.DifferenceType.QUANTITY_MISMATCH);
            diff.setOrderQuantity(order.getQuantity());
            diff.setPaymentQuantity(payment.getQuantity());
            diff.setQuantityDifference(Math.abs(order.getQuantity() - payment.getQuantity()));
            diff.setRemark(String.format("库存扣减不一致: 订单数量 %d, 支付数量 %d",
                    order.getQuantity(), payment.getQuantity()));
            diffs.add(diff);
        }

        return diffs;
    }

    private BigDecimal convertToBaseCurrency(BigDecimal amount, String currency, LocalDate rateDate) {
        if (currency.equals(baseCurrency)) {
            return amount;
        }
        BigDecimal rate = getExchangeRate(currency, baseCurrency, rateDate);
        if (rate == null) {
            log.warn("No exchange rate found for {}/{} on {}, using 1.0", currency, baseCurrency, rateDate);
            return amount;
        }
        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal getExchangeRate(String sourceCurrency, String targetCurrency, LocalDate rateDate) {
        return exchangeRateMapper.findRate(sourceCurrency, targetCurrency, rateDate);
    }

    private ReconciliationTask createTask(LocalDate startDate, LocalDate endDate) {
        ReconciliationTask task = new ReconciliationTask();
        task.setStartDate(startDate);
        task.setEndDate(endDate);
        task.setStatus(ReconciliationTask.TaskStatus.PENDING);
        task.setTotalCount(0);
        task.setMatchCount(0);
        task.setDifferenceCount(0);
        task.setTotalOrderAmount(BigDecimal.ZERO);
        task.setTotalPaymentAmount(BigDecimal.ZERO);
        task.setTotalDifference(BigDecimal.ZERO);
        taskMapper.insert(task);
        return task;
    }

    private void updateTaskCompletion(ReconciliationTask task, int totalCount, int matchCount,
                                       int diffCount, BigDecimal totalOrderAmount, BigDecimal totalPaymentAmount) {
        task.setStatus(ReconciliationTask.TaskStatus.COMPLETED);
        task.setTotalCount(totalCount);
        task.setMatchCount(matchCount);
        task.setDifferenceCount(diffCount);
        task.setTotalOrderAmount(totalOrderAmount);
        task.setTotalPaymentAmount(totalPaymentAmount);
        task.setTotalDifference(totalOrderAmount.subtract(totalPaymentAmount).abs());
        task.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private ReconciliationDifference buildDifference(Long taskId, OrderDTO order, PaymentDTO payment,
                                                      ReconciliationDifference.DifferenceType type, String remark) {
        ReconciliationDifference diff = new ReconciliationDifference();
        diff.setTaskId(taskId);
        diff.setOrderNo(order.getOrderNo());
        diff.setDifferenceType(type);
        diff.setOrderAmount(order.getAmount());
        diff.setOrderCurrency(order.getCurrency());
        diff.setOrderQuantity(order.getQuantity());
        diff.setRemark(remark);
        return diff;
    }

    private ReconciliationDifference buildDifferenceForPaymentOnly(Long taskId, PaymentDTO payment, String remark) {
        ReconciliationDifference diff = new ReconciliationDifference();
        diff.setTaskId(taskId);
        diff.setOrderNo(payment.getOrderNo());
        diff.setPaymentTransactionId(payment.getTransactionId());
        diff.setDifferenceType(ReconciliationDifference.DifferenceType.PAYMENT_ONLY);
        diff.setPaymentAmount(payment.getAmount());
        diff.setPaymentCurrency(payment.getCurrency());
        diff.setPaymentQuantity(payment.getQuantity());
        diff.setRemark(remark);
        return diff;
    }

    private ReconciliationResultDTO buildResult(ReconciliationTask task, List<ReconciliationDifference> diffs) {
        ReconciliationResultDTO result = new ReconciliationResultDTO();
        result.setTaskId(task.getId());
        result.setStatus(task.getStatus().name());
        result.setTotalCount(task.getTotalCount());
        result.setMatchCount(task.getMatchCount());
        result.setDifferenceCount(task.getDifferenceCount());
        result.setTotalOrderAmount(task.getTotalOrderAmount());
        result.setTotalPaymentAmount(task.getTotalPaymentAmount());
        result.setTotalDifference(task.getTotalDifference());
        result.setStartedAt(task.getStartedAt());
        result.setCompletedAt(task.getCompletedAt());

        List<ReconciliationResultDTO.ReconciliationDifference> diffDTOs = diffs.stream().map(d -> {
            ReconciliationResultDTO.ReconciliationDifference dto = new ReconciliationResultDTO.ReconciliationDifference();
            dto.setOrderNo(d.getOrderNo());
            dto.setPaymentTransactionId(d.getPaymentTransactionId());
            dto.setDifferenceType(d.getDifferenceType().name());
            dto.setOrderAmount(d.getOrderAmount());
            dto.setOrderCurrency(d.getOrderCurrency());
            dto.setPaymentAmount(d.getPaymentAmount());
            dto.setPaymentCurrency(d.getPaymentCurrency());
            dto.setAmountDifference(d.getAmountDifference());
            dto.setExchangeRateUsed(d.getExchangeRateUsed());
            dto.setQuantityDifference(d.getQuantityDifference());
            dto.setRemark(d.getRemark());
            return dto;
        }).collect(Collectors.toList());

        result.setDifferences(diffDTOs);
        return result;
    }

    public ReconciliationResultDTO getTaskResult(Long taskId) {
        ReconciliationTask task = taskMapper.selectById(taskId);
        if (task == null) {
            throw new RuntimeException("对账任务不存在: " + taskId);
        }

        List<ReconciliationDifference> diffs = differenceMapper.selectList(
                new LambdaQueryWrapper<ReconciliationDifference>().eq(ReconciliationDifference::getTaskId, taskId));

        return buildResult(task, diffs);
    }

    public List<ReconciliationTask> listTasks(LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<ReconciliationTask> wrapper = new LambdaQueryWrapper<>();
        if (startDate != null) {
            wrapper.ge(ReconciliationTask::getStartDate, startDate);
        }
        if (endDate != null) {
            wrapper.le(ReconciliationTask::getEndDate, endDate);
        }
        wrapper.orderByDesc(ReconciliationTask::getCreatedAt);
        return taskMapper.selectList(wrapper);
    }
}
