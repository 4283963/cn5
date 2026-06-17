package com.reconciliation.controller;

import com.reconciliation.dto.ApiResponse;
import com.reconciliation.dto.ReconciliationRequest;
import com.reconciliation.dto.ReconciliationResultDTO;
import com.reconciliation.entity.ReconciliationDifference;
import com.reconciliation.entity.ReconciliationTask;
import com.reconciliation.service.AutoFixService;
import com.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final AutoFixService autoFixService;

    @PostMapping("/trigger")
    public ApiResponse<ReconciliationResultDTO> triggerReconciliation(
            @Valid @RequestBody ReconciliationRequest request) {
        if (request.getStartDate().isAfter(request.getEndDate())) {
            return ApiResponse.fail(400, "开始日期不能晚于结束日期");
        }
        ReconciliationResultDTO result = reconciliationService.reconcile(
                request.getStartDate(), request.getEndDate());
        return ApiResponse.ok(result);
    }

    @GetMapping("/task/{taskId}")
    public ApiResponse<ReconciliationResultDTO> getTaskResult(@PathVariable Long taskId) {
        ReconciliationResultDTO result = reconciliationService.getTaskResult(taskId);
        return ApiResponse.ok(result);
    }

    @GetMapping("/tasks")
    public ApiResponse<List<ReconciliationTask>> listTasks(
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        List<ReconciliationTask> tasks = reconciliationService.listTasks(startDate, endDate);
        return ApiResponse.ok(tasks);
    }

    @PostMapping("/auto-fix/task/{taskId}")
    public ApiResponse<Map<String, Object>> autoFixTask(@PathVariable Long taskId) {
        int fixedCount = autoFixService.autoFixTask(taskId);
        return ApiResponse.ok(Map.of(
                "taskId", taskId,
                "fixedCount", fixedCount,
                "message", String.format("成功自动补账 %d 笔", fixedCount)
        ));
    }

    @PostMapping("/auto-fix/difference/{differenceId}")
    public ApiResponse<Map<String, Object>> autoFixDifference(@PathVariable Long differenceId) {
        boolean success = autoFixService.autoFixSingleDifference(differenceId);
        return ApiResponse.ok(Map.of(
                "differenceId", differenceId,
                "success", success,
                "message", success ? "自动补账成功" : "自动补账失败"
        ));
    }

    @GetMapping("/auto-fix/pending/{taskId}")
    public ApiResponse<List<ReconciliationDifference>> getPendingAutoFix(@PathVariable Long taskId) {
        List<ReconciliationDifference> pending = autoFixService.getPendingAutoFixDifferences(taskId);
        return ApiResponse.ok(pending);
    }
}
