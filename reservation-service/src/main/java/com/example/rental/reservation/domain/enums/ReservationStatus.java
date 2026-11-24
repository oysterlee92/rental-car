package com.example.rental.reservation.domain.enums;

/** 예약의 차량 선점 상태다. */
public enum ReservationStatus {
    /** 결제를 기다리는 동안 차량을 선점한 상태다. */
    HELD,
    /** 선점 유효 시간이 끝난 상태다. */
    EXPIRED
}