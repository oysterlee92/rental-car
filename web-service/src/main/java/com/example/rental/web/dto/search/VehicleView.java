package com.example.rental.web.dto.search;

/**
 * 차량 목록 화면에 전달할 모델이다.
 *
 * @param id 차량 ID
 * @param name 차량 이름
 * @param seats 탑승 인원
 * @param dailyPrice 원 단위 일일 요금
 */
public record VehicleView(
        Long id,
        String name,
        int seats,
        long dailyPrice
) {
}