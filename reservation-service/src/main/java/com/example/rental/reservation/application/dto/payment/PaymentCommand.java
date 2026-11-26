package com.example.rental.reservation.application.dto.payment;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 결제 서비스에 보낼 고정된 예약 조건이다.
 * @param reservationId 예약 ID이자 결제 중복 방지 키
 * @param amount 예약에 저장한 금액
 * @param expiresAt 승인 가능한 기한
 */
public record PaymentCommand(UUID reservationId, long amount, LocalDateTime expiresAt) { }