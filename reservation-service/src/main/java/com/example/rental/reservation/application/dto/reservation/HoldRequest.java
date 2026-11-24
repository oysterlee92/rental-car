package com.example.rental.reservation.application.dto.reservation;

import java.time.LocalDateTime;

/**
 * 선점 요청의 입력값이다.
 *
 * @param branchId 인수·반납 지점
 * @param vehicleId 선점할 차량
 * @param pickupAt 인수 시각
 * @param returnAt 반납 시각
 */
public record HoldRequest(
        Long branchId,
        Long vehicleId,
        LocalDateTime pickupAt,
        LocalDateTime returnAt
) {
    /**
     * 필수 입력과 차량 ID를 확인한다.
     *
     * @param branchId 지점 ID
     * @param vehicleId 차량 ID
     * @param pickupAt 인수 시각
     * @param returnAt 반납 시각
     */
    public HoldRequest {
        if (branchId == null || vehicleId == null || vehicleId <= 0
                || pickupAt == null || returnAt == null) {
            throw new IllegalArgumentException("지점, 차량과 인수·반납 시각을 입력해 주세요.");
        }
    }
}