package com.example.rental.reservation.infrastructure.repository.jpa;

import com.example.rental.reservation.domain.entity.Reservation;
import com.example.rental.reservation.domain.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

/** 예약을 저장하고 상태 변경 전에 행 잠금을 획득한다. */
public interface JpaReservationRepository extends JpaRepository<Reservation, UUID> {
    /**
     * 예약 ID로 행을 잠그고 조회한다.
     *
     * @param id 예약 ID
     * @return 조회한 예약
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findOneById(UUID id);

    /**
     * 요청 키로 기존 예약을 잠그고 조회한다.
     *
     * @param requestKey 같은 요청을 구분할 키
     * @return 기존 예약
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findByRequestKey(UUID requestKey);

    /**
     * 만료 대상 중 지정한 페이지를 기한·ID 순서로 잠그고 조회한다.
     *
     * @param status 선점 상태
     * @param now 만료 판단 시각
     * @param page 처리할 최대 건수
     * @return 만료 대상 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Reservation> findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(ReservationStatus status, LocalDateTime now, Pageable page);

    /**
     * 해당 차량의 만료 대상을 기한·ID 순서로 잠그고 조회한다.
     *
     * @param vehicleId 차량 ID
     * @param status 선점 상태
     * @param now 만료 판단 시각
     * @return 만료 대상 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Reservation> findByVehicleIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(long vehicleId, ReservationStatus status, LocalDateTime now);
}