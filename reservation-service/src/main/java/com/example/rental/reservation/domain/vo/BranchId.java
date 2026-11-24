package com.example.rental.reservation.domain.vo;

/**
 * 지점 ID를 담고, 0 이하의 ID가 만들어지지 않도록 검사한다.
 *
 * @param value 지점 ID
 */
public record BranchId(long value) {
    /**
     * 유효한 지점 ID만 생성한다.
     *
     * @param value 지점 ID
     * @throws IllegalArgumentException ID가 양수가 아닌 경우
     */
    public BranchId {
        if (value <= 0) {
            throw new IllegalArgumentException("지점 ID는 0보다 커야 합니다.");
        }
    }
}