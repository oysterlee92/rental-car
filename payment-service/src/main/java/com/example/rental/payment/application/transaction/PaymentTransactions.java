package com.example.rental.payment.application.transaction;

import com.example.rental.payment.application.dto.PaymentRequest;
import com.example.rental.payment.application.dto.PaymentResponse;
import com.example.rental.payment.domain.repository.PaymentRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 결제 요청과 최종 결과를 짧은 트랜잭션으로 저장한다. */
@Service
public class PaymentTransactions {
    private final PaymentRepository payments;
    private final Clock clock;

    /**
     * 저장소와 현재 시각을 제공하는 시계를 받는다.
     * @param payments 도메인 저장소
     * @param clock 현재 시각을 제공하는 시계
     */
    public PaymentTransactions(PaymentRepository payments, Clock clock) {
        this.payments = payments;
        this.clock = clock;
    }

    /**
     * 최초 요청을 기록하거나 기존 요청의 조건과 결과를 확인한다.
     * @param request 결제 조건
     * @return 저장한 결제의 현재 상태
     */
    @Transactional
    public PaymentResponse open(PaymentRequest request) {
        var payment = payments.open(request.reservationId(), request.amount(), request.expiresAt());
        payment.requireSameRequest(request.amount(), request.expiresAt());
        return PaymentResponse.from(payment);
    }

    /**
     * 모의 승인 결과를 반영하며 기한 초과 시 실패로 확정한다.
     * @param request 원래 결제 조건
     * @param approved 모의 승인 결과
     * @return DB에 저장한 최종 결과
     */
    @Transactional
    public PaymentResponse complete(PaymentRequest request, boolean approved) {
        var payment = payments.findForUpdate(request.reservationId());
        payment.requireSameRequest(request.amount(), request.expiresAt());
        payment.complete(approved, LocalDateTime.now(clock));
        return PaymentResponse.from(payment);
    }
}