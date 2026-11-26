package com.example.rental.reservation.application.dto.payment;

import com.example.rental.reservation.application.dto.reservation.ReservationResponse;
import java.time.Duration;

/**
 * 예약 결과 반영과 관측에 사용할 값이다.
 * @param reservation 현재 예약
 * @param confirmedNow 이번 트랜잭션에서 새로 확정했는지 여부
 * @param elapsed 예약 접수부터 확정까지 걸린 시간
 */
public record CheckoutCompletion(ReservationResponse reservation, boolean confirmedNow, Duration elapsed) { }