package com.example.rental.reservation.domain.repository;

import com.example.rental.reservation.domain.entity.Branch;
import com.example.rental.reservation.domain.vo.BranchId;
import java.util.List;

/**
 * 지점 목록 조회와 지점 존재 여부 확인에 필요한 메서드를 정의한다.
 */
public interface BranchRepository {
    /**
     * ID 오름차순으로 모든 지점을 조회한다.
     *
     * @return 지점 목록
     */
    List<Branch> findAll();

    /**
     * 지정한 지점이 등록되어 있는지 확인한다.
     *
     * @param branchId 유효한 지점 ID
     * @return 등록된 지점이면 true
     */
    boolean exists(BranchId branchId);
}