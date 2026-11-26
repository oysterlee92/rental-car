package com.example.rental.reservation.domain.enums;

/** 예약의 선점과 결제·확정 상태다. */
public enum ReservationStatus {
    /** 결제를 기다리는 동안 차량을 선점한 상태다. */
    HELD,
    /** 선점 유효 시간이 끝난 상태다. */
    EXPIRED,
    /** 결제 결과가 확정될 때까지 차량 배정을 유지한다. */
    PAYMENT_PENDING,
    /** 결제가 승인돼 예약이 확정됐다. */
    CONFIRMED,
    /** 결제가 거절돼 배정을 해제했다. */
    PAYMENT_FAILED
}