package com.reconciliation.controller;

import com.reconciliation.dto.ApiResponse;
import com.reconciliation.dto.ReconciliationRequest;
import com.reconciliation.dto.ReconciliationResultDTO;
import com.reconciliation.entity.ReconciliationTask;
import com.reconciliation.service.ReconciliationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

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
}
