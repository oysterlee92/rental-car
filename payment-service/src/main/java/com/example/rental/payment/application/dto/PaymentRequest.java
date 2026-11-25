package com.example.rental.payment.application.dto;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * 결제 요청의 고정 조건이다.
 * @param reservationId 예약 ID이자 중복 방지 키
 * @param amount 원 단위 금액
 * @param expiresAt 승인 가능한 기한
 */
public record PaymentRequest(UUID reservationId, long amount, LocalDateTime expiresAt) {
    /**
     * 필수 값과 금액을 검사하고 시각 정밀도를 통일한다.
     * @throws IllegalArgumentException 필수 값이 없거나 금액이 음수인 경우
     */
    public PaymentRequest {
        if (reservationId == null || amount < 0 || expiresAt == null) {
            throw new IllegalArgumentException("예약 ID와 결제 금액, 승인 기한을 확인해 주세요.");
        }
        expiresAt = expiresAt.truncatedTo(ChronoUnit.MICROS);
    }
}