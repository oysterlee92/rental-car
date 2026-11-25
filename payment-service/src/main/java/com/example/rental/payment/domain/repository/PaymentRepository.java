package com.example.rental.payment.domain.repository;

import com.example.rental.payment.domain.entity.Payment;
import java.time.LocalDateTime;
import java.util.UUID;

/** 결제 조회와 최초 요청 저장을 제공한다. */
public interface PaymentRepository {
    /**
     * 최초 결제를 저장하거나 같은 키의 결제를 잠금 조회한다.
     * @param id 예약 ID
     * @param amount 결제 금액
     * @param expiresAt 승인 기한
     * @return 잠금 조회한 결제
     */
    Payment open(UUID id, long amount, LocalDateTime expiresAt);

    /**
     * 결과 반영을 위해 결제를 잠금 조회한다.
     * @param id 예약 ID
     * @return 기존 결제
     */
    Payment findForUpdate(UUID id);
}