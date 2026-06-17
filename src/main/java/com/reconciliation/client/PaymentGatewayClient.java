package com.reconciliation.client;

import com.reconciliation.dto.PaymentDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
public class PaymentGatewayClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PaymentGatewayClient(RestTemplate restTemplate,
                                @Value("${external.payment-gateway.base-url}") String paymentGatewayBaseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = paymentGatewayBaseUrl;
    }

    public List<PaymentDTO> fetchPayments(LocalDate startDate, LocalDate endDate) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/list")
                .queryParam("startDate", startDate.format(DateTimeFormatter.ISO_DATE))
                .queryParam("endDate", endDate.format(DateTimeFormatter.ISO_DATE))
                .toUriString();

        log.info("Fetching payments from: {}", url);

        try {
            ResponseEntity<ApiResponse<List<PaymentDTO>>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getBody() != null && response.getBody().code() == 200) {
                return response.getBody().data();
            }
            log.warn("Payment gateway returned non-200: {}", response.getBody());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to fetch payments from payment gateway", e);
            throw new RuntimeException("支付网关调用失败: " + e.getMessage(), e);
        }
    }

    record ApiResponse<T>(int code, String message, T data) {}
}
