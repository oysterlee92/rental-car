package com.example.rental.reservation.application.dto.vehicle;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 검색 조건과 조회 결과를 묶은 응답이다.
 *
 * @param branchId 검색한 지점 ID
 * @param pickupAt 인수 시각
 * @param returnAt 반납 시각
 * @param vehicles 운영 가능한 차량 목록
 */
public record SearchResponse(
        Long branchId,
        LocalDateTime pickupAt,
        LocalDateTime returnAt,
        List<VehicleResponse> vehicles
) {
}