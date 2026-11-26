package com.example.rental.payment.presentation.controller;

import com.example.rental.payment.application.dto.PaymentRequest;
import com.example.rental.payment.application.dto.PaymentResponse;
import com.example.rental.payment.application.service.PaymentService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** 모의 결제 API를 제공한다. */
@RestController
public class PaymentController {
    private final PaymentService payments;

    /** @param payments 모의 결제 서비스 */
    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /**
     * 예약 ID로 결제하거나 이미 저장한 결제 결과를 반환한다.
     * @param request 예약에서 확정한 결제 조건
     * @return 결제 최종 결과
     */
    @PostMapping("/api/payments")
    public PaymentResponse pay(@RequestBody PaymentRequest request) {
        return payments.pay(request);
    }
}