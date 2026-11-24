package com.example.rental.reservation.infrastructure.repository.adapter;

import com.example.rental.reservation.domain.entity.Branch;
import com.example.rental.reservation.domain.repository.BranchRepository;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.infrastructure.repository.jpa.JpaBranchRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * BranchRepository의 메서드를 호출받아 JpaBranchRepository에 DB 조회를 요청한다.
 */
@Repository
public class BranchRepositoryAdapter implements BranchRepository {
    private final JpaBranchRepository jpa;

    /**
     * DB 조회를 실행할 Spring Data JPA 저장소를 생성자로 받는다.
     *
     * @param jpa JPA 지점 저장소
     */
    public BranchRepositoryAdapter(JpaBranchRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * ID 순서대로 지점을 조회한다.
     *
     * @return 지점 목록
     */
    @Override
    public List<Branch> findAll() {
        return jpa.findAllByOrderByIdAsc();
    }

    /**
     * BranchId에서 숫자를 꺼내 해당 지점이 DB에 있는지 확인한다.
     *
     * @param branchId 지점 ID
     * @return 지점이 있으면 true
     */
    @Override
    public boolean exists(BranchId branchId) {
        return jpa.existsById(branchId.value());
    }
}