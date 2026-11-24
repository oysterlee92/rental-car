package com.example.rental.web.dto.search;

/**
 * Web에서 사용하는 지점 응답 모델이다.
 *
 * @param id 지점 ID
 * @param name 지점 이름
 */
public record BranchView(Long id, String name) {
}