package com.example.rental.reservation.domain.enums;

/**
 * 차량의 운영 상태다. 기간별 예약 배정은 별도 데이터로 관리한다.
 */
public enum VehicleStatus {
    /** 운영 가능한 차량이다. */
    AVAILABLE,
    /** 정비 중이어서 검색에서 제외할 차량이다. */
    MAINTENANCE
}