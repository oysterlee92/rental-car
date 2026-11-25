package com.example.rental.payment.application.dto;

import com.example.rental.payment.domain.entity.Payment;
import com.example.rental.payment.domain.enums.PaymentStatus;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 결과와 최초 요청 조건이다.
 * @param reservationId 예약 ID
 * @param amount 결제 금액
 * @param expiresAt 승인 기한
 * @param status 결제 상태
 */
public record PaymentResponse(
        UUID reservationId,
        long amount,
        LocalDateTime expiresAt,
        PaymentStatus status
) {
    /**
     * 결제 엔티티를 응답으로 바꾼다.
     * @param payment 조회한 결제
     * @return 결제 조건과 상태
     */
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getReservationId(),
                payment.getAmount(),
                payment.getExpiresAt(),
                payment.getStatus()
        );
    }
}