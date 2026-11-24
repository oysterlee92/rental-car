package com.example.rental.reservation.application.dto.reservation;

/**
 * 선점 결과와 처음 생성한 요청인지 여부다.
 *
 * @param reservation 예약 응답
 * @param created 새로 생성했으면 true
 */
public record HoldResult(ReservationResponse reservation, boolean created) {
}