package com.example.rental.payment.application.service;

import lombok.extern.slf4j.Slf4j;
import com.example.rental.payment.application.transaction.PaymentTransactions;
import com.example.rental.payment.application.dto.PaymentRequest;
import com.example.rental.payment.application.dto.PaymentResponse;
import com.example.rental.payment.application.port.ApprovalGateway;
import com.example.rental.payment.domain.enums.PaymentStatus;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 결제 접수·모의 승인·결과 저장의 순서를 관리한다. */
@Slf4j
@Service
public class PaymentService {
    private final PaymentTransactions transactions;
    private final ApprovalGateway approvals;
    private final ObservationRegistry observations;

    /**
     * DB 처리와 모의 승인, 관측 도구를 받는다.
     * @param transactions 결제 트랜잭션
     * @param approvals 모의 승인
     * @param observations 추적·지연 기록
     */
    public PaymentService(PaymentTransactions transactions, ApprovalGateway approvals, ObservationRegistry observations) {
        this.transactions = transactions;
        this.approvals = approvals;
        this.observations = observations;
    }

    /**
     * 동일 예약 ID의 결제 결과를 한 번 확정하고 반환한다.
     * @param request 예약 ID·금액·기한
     * @return 최종 결제 결과
     */
    @Transactional(propagation = Propagation.NEVER)
    public PaymentResponse pay(PaymentRequest request) {
        try (var ignored = MDC.putCloseable("reservationId", request.reservationId().toString())) {
            var existing = transactions.open(request);
            if (existing.status() != PaymentStatus.PENDING) {
                return existing;
            }
            boolean approved = Observation.createNotStarted("payment.mock", observations)
                    .highCardinalityKeyValue("reservation.id", request.reservationId().toString())
                    .observe(approvals::approve);
            var result = transactions.complete(request, approved);
            log.info("모의 결제 결과를 저장했습니다. 상태={}", result.status());
            return result;
        }
    }
}