package com.example.rental.reservation.application.dto.vehicle;

/**
 * 검색 결과에 표시할 차량 정보다.
 *
 * @param id 차량 ID
 * @param name 차량 이름
 * @param seats 탑승 인원
 * @param dailyPrice 원 단위 일일 요금
 */
public record VehicleResponse(
        Long id,
        String name,
        int seats,
        long dailyPrice
) {
}