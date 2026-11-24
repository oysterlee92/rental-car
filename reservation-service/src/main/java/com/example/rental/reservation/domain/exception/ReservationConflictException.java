package com.example.rental.reservation.domain.exception;

/** 차량 선점이나 요청 키 사용 규칙이 충돌했을 때 발생한다. */
public class ReservationConflictException extends RuntimeException {
    /**
     * 충돌 이유를 메시지로 전달한다.
     *
     * @param message 사용자에게 안내할 경어체 오류 메시지
     */
    public ReservationConflictException(String message) {
        super(message);
    }
}