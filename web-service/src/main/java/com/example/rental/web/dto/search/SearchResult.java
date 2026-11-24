package com.example.rental.web.dto.search;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reservation이 보낸 JSON 검색 응답을 읽어 담는 record다.
 *
 * @param branchId 검색한 지점 ID
 * @param pickupAt API가 반환한 인수 시각
 * @param returnAt API가 반환한 반납 시각
 * @param vehicles 검색 결과 차량 목록
 */
public record SearchResult(
        Long branchId,
        LocalDateTime pickupAt,
        LocalDateTime returnAt,
        List<VehicleView> vehicles
) {
}