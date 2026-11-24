package com.example.rental.reservation.infrastructure.repository.jpa;

import com.example.rental.reservation.domain.entity.Vehicle;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** PostgreSQL의 기간 비교로 검색 가능한 차량을 조회한다. */
public interface JpaVehicleRepository extends JpaRepository<Vehicle, Long> {
    /**
     * 같은 기간의 유효한 선점이 없는 차량을 조회한다.
     *
     * @param branchId 지점 ID
     * @param pickupAt 인수 시각
     * @param returnAt 반납 시각
     * @param now 만료 판단 시각
     * @return 운영 가능하고 기간이 비어 있는 차량
     */
    @Query(value = """
            SELECT v.*
            FROM vehicle v
            WHERE v.branch_id = :branchId
              AND v.status = 'AVAILABLE'
              AND NOT EXISTS (
                  SELECT 1
                  FROM reservation r
                  WHERE r.vehicle_id = v.id
                    AND r.status = 'HELD'
                    AND r.expires_at > :now
                    AND r.during && tsrange(CAST(:pickupAt AS timestamp without time zone), CAST(:returnAt AS timestamp without time zone), '[)')
              )
            ORDER BY v.id
            """, nativeQuery = true)
    List<Vehicle> findAvailable(
            @Param("branchId") long branchId,
            @Param("pickupAt") LocalDateTime pickupAt,
            @Param("returnAt") LocalDateTime returnAt,
            @Param("now") LocalDateTime now
    );
}