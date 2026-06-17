package com.reconciliation.client;

import com.reconciliation.dto.OrderDTO;
import com.reconciliation.dto.OrderStatusUpdateRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class OrderServiceClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrderServiceClient(RestTemplate restTemplate,
                              @Value("${external.order-service.base-url}") String orderServiceBaseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = orderServiceBaseUrl;
    }

    public List<OrderDTO> fetchOrders(LocalDate startDate, LocalDate endDate) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/list")
                .queryParam("startDate", startDate.format(DateTimeFormatter.ISO_DATE))
                .queryParam("endDate", endDate.format(DateTimeFormatter.ISO_DATE))
                .toUriString();

        log.info("Fetching orders from: {}", url);

        try {
            ResponseEntity<ApiResponse<List<OrderDTO>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getBody() != null && response.getBody().code() == 200) {
                return response.getBody().data();
            }
            log.warn("Order service returned non-200: {}", response.getBody());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch orders from order service", e);
            throw new RuntimeException("订单服务调用失败: " + e.getMessage(), e);
        }
    }

    public boolean updateOrderStatus(OrderStatusUpdateRequest request) {
        String url = baseUrl + "/update-status";
        log.info("Updating order status for {}: {}", request.getOrderNo(), request.getStatus());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getBody() != null && response.getBody().code() == 200) {
                log.info("Order {} status updated successfully", request.getOrderNo());
                return true;
            }
            log.warn("Order service update returned non-200 for {}: {}", request.getOrderNo(), response.getBody());
            return false;
        } catch (Exception e) {
            log.error("Failed to update order status for {}", request.getOrderNo(), e);
            throw new RuntimeException("订单状态更新失败: " + e.getMessage(), e);
        }
    }

    record ApiResponse<T>(int code, String message, T data) {}
}
