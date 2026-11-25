package com.example.rental.payment.domain.entity;

import com.example.rental.payment.domain.enums.PaymentStatus;
import com.example.rental.payment.domain.exception.PaymentConflictException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import java.util.UUID;

/** 하나의 예약에 대한 모의 승인 결과를 관리한다. */
@Entity
public class Payment {
    @Id
    private UUID reservationId;
    private long amount;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    /** JPA가 DB에서 읽은 결제를 생성할 때 사용한다. */
    protected Payment() { }

    /**
     * 같은 예약 ID로 들어온 요청 조건을 확인한다.
     * @param requestedAmount 재요청 금액
     * @param requestedExpiry 재요청 승인 기한
     * @throws PaymentConflictException 최초 요청 조건과 다른 경우
     */
    public void requireSameRequest(long requestedAmount, LocalDateTime requestedExpiry) {
        if (amount != requestedAmount || !expiresAt.equals(requestedExpiry)) {
            throw new PaymentConflictException();
        }
    }

    /**
     * 행 잠금을 얻은 뒤 최종 결과를 한 번만 기록한다.
     * @param approved 모의 승인 여부
     * @param now 결과 저장 시각
     */
    public void complete(boolean approved, LocalDateTime now) {
        if (status != PaymentStatus.PENDING) {
            return;
        }
        // 처리 도중 기한이 지났다면 승인하지 않는다.
        status = approved && expiresAt.isAfter(now) ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED;
        completedAt = now;
    }

    public UUID getReservationId() { return reservationId; }
    public long getAmount() { return amount; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public PaymentStatus getStatus() { return status; }
}