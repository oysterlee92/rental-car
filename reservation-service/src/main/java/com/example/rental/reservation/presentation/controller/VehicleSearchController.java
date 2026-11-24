package com.example.rental.reservation.presentation.controller;

import com.example.rental.reservation.application.dto.vehicle.BranchResponse;
import com.example.rental.reservation.application.dto.vehicle.SearchResponse;
import com.example.rental.reservation.application.service.VehicleSearchService;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지점·차량 조회 요청을 받아 입력값을 변환하고 VehicleSearchService를 호출한다.
 */
@RestController
public class VehicleSearchController {
    private final VehicleSearchService service;

    /**
     * 검색 흐름을 담당할 서비스를 주입받는다.
     *
     * @param service 차량 검색 애플리케이션 서비스
     */
    public VehicleSearchController(VehicleSearchService service) {
        this.service = service;
    }

    /**
     * 검색 화면의 지점 목록을 반환한다.
     *
     * @return 지점 ID와 이름 목록
     */
    @GetMapping("/api/branches")
    public List<BranchResponse> branches() {
        return service.branches();
    }

    /**
     * 요청의 지점 ID와 시각으로 BranchId와 RentalPeriod를 만들고 차량을 조회한다.
     *
     * @param branchId 검색할 지점 ID
     * @param pickupAt 인수 시각
     * @param returnAt 반납 시각
     * @return 검색 조건과 차량 목록
     */
    @GetMapping("/api/vehicles")
    public SearchResponse search(@RequestParam Long branchId, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime pickupAt, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime returnAt) {
        // 입력한 시각으로 대여 기간을 만든다.
        var period = new RentalPeriod(pickupAt, returnAt);
        return service.search(new BranchId(branchId), period);
    }
}