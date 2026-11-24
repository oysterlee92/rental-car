package com.example.rental.reservation.domain.repository;

import com.example.rental.reservation.domain.entity.Vehicle;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** 차량 검색과 예약 생성에 필요한 조회 메서드를 정의한다. */
public interface VehicleRepository {
    /**
     * 운영 상태와 기간별 선점을 확인해 차량을 조회한다.
     *
     * @param branchId 검색 지점
     * @param period 검색 기간
     * @param now 선점 만료 판단 시각
     * @return ID 오름차순 검색 결과
     */
    List<Vehicle> findAvailableByBranch(BranchId branchId, RentalPeriod period, LocalDateTime now);

    /**
     * 예약할 차량의 현재 정보를 조회한다.
     *
     * @param vehicleId 차량 ID
     * @return 차량 정보
     */
    Optional<Vehicle> findById(long vehicleId);
}