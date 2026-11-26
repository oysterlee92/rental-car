package com.example.rental.reservation.application.dto.payment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 서비스가 반환하는 최종 결과다.
 * @param reservationId 예약 ID
 * @param amount 결제 금액
 * @param expiresAt 최초 요청의 승인 기한
 * @param status SUCCEEDED 또는 FAILED
 */
public record PaymentResult(
        UUID reservationId,
        long amount,
        LocalDateTime expiresAt,
        String status
) { }