package com.example.rental.reservation.application.service;

import com.example.rental.reservation.application.dto.vehicle.BranchResponse;
import com.example.rental.reservation.application.dto.vehicle.SearchResponse;
import com.example.rental.reservation.application.dto.vehicle.VehicleResponse;
import com.example.rental.reservation.application.exception.BranchNotFoundException;
import com.example.rental.reservation.domain.repository.BranchRepository;
import com.example.rental.reservation.domain.repository.VehicleRepository;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지점이 있는지 확인하고 차량을 조회한 뒤 화면에 필요한 결과를 만든다.
 */
@Service
@Transactional(readOnly = true)
public class VehicleSearchService {
    private final BranchRepository branches;
    private final VehicleRepository vehicles;
    private final Clock clock;

    /**
     * 지점과 차량을 조회할 객체를 생성자로 받는다. Spring이 각 JPA 어댑터를 연결한다.
     *
     * @param branches 지점 저장소
     * @param vehicles 차량 저장소
     * @param clock 선점 만료를 판단할 설정한 시간대의 시계
     */
    public VehicleSearchService(BranchRepository branches, VehicleRepository vehicles, Clock clock) {
        this.branches = branches;
        this.vehicles = vehicles;
        this.clock = clock;
    }

    /**
     * 지점 엔티티를 화면에 필요한 응답으로 변환한다.
     *
     * @return ID 오름차순 지점 목록
     */
    public List<BranchResponse> branches() {
        return branches.findAll().stream()
                .map(branch -> new BranchResponse(branch.getId(), branch.getName()))
                .toList();
    }

    /**
     * 지점을 확인하고 운영 가능한 차량과 검색 조건을 반환한다.
     *
     * @param branchId 유효한 지점 ID
     * @param period 유효한 대여 기간
     * @return 검색 조건과 차량 목록
     * @throws BranchNotFoundException 지점이 없는 경우
     */
    public SearchResponse search(BranchId branchId, RentalPeriod period) {
        // 지점 ID가 양수인지, 반납이 인수보다 늦은지는 BranchId와 RentalPeriod가 이미 검사했다.
        if (!branches.exists(branchId)) {
            throw new BranchNotFoundException(branchId);
        }
        // VehicleRepository에 차량 조회를 요청한다. 실제 JPA 호출은 VehicleRepositoryAdapter가 담당한다.
        var results = vehicles.findAvailableByBranch(branchId, period, LocalDateTime.now(clock)).stream()
                .map(vehicle -> new VehicleResponse(
                        vehicle.getId(),
                        vehicle.getName(),
                        vehicle.getSeats(),
                        vehicle.getDailyPrice()
                ))
                .toList();
        return new SearchResponse(
                branchId.value(),
                period.pickupAt(),
                period.returnAt(),
                results
        );
    }

}