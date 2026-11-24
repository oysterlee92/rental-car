package com.example.rental.reservation.application.dto.vehicle;

/**
 * 지점 선택에 필요한 응답이다.
 *
 * @param id 지점 ID
 * @param name 지점 이름
 */
public record BranchResponse(Long id, String name) {
}