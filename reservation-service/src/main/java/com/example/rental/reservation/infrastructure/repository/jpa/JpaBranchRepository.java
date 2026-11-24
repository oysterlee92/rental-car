package com.example.rental.reservation.infrastructure.repository.jpa;

import com.example.rental.reservation.domain.entity.Branch;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA가 메서드 이름을 보고 지점 조회 쿼리를 만드는 인터페이스다.
 */
public interface JpaBranchRepository extends JpaRepository<Branch, Long> {
    /**
     * 지점을 ID 오름차순으로 조회한다.
     *
     * @return 지점 목록
     */
    List<Branch> findAllByOrderByIdAsc();
}