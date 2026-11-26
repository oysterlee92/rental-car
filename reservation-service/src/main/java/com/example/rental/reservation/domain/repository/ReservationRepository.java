package com.example.rental.reservation.domain.repository;

import com.example.rental.reservation.domain.entity.Reservation;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 예약 애그리거트를 저장하고 상태 변경에 필요한 잠금 조회를 제공한다. */
public interface ReservationRepository {
    /**
     * 예약을 저장하고 DB 제약까지 즉시 검사한다.
     *
     * @param reservation 저장할 예약
     * @return 저장된 예약
     * @throws ReservationConflictException 차량의 배정 기간이 겹치는 경우
     */
    Reservation saveAndFlush(Reservation reservation);

    /**
     * 예약 행을 잠그고 조회한다. 호출한 트랜잭션이 끝나면 잠금이 풀린다.
     *
     * @param id 예약 ID
     * @return 존재하는 예약
     */
    Optional<Reservation> findByIdForUpdate(UUID id);

    /**
     * 요청 키로 기존 예약을 잠그고 조회한다.
     *
     * @param requestKey 재전송 확인용 키
     * @return 해당 키로 만든 예약
     */
    Optional<Reservation> findByRequestKeyForUpdate(UUID requestKey);

    /**
     * 만료 기한에 도달한 선점을 기한·ID 순서로 잠그고 조회한다.
     *
     * @param now 현재 시각
     * @param limit 한 번에 처리할 최대 건수
     * @return 만료 처리 대상
     */
    List<Reservation> findDueForUpdate(LocalDateTime now, int limit);

    /**
     * 특정 차량의 만료 대상을 같은 잠금 순서로 조회한다.
     *
     * @param vehicleId 선점할 차량 ID
     * @param now 현재 시각
     * @return 해당 차량의 만료 대상
     */
    List<Reservation> findDueForVehicleForUpdate(long vehicleId, LocalDateTime now);
    /**
     * 결제 결과를 기다리는 예약을 오래된 순서로 조회한다.
     * @param limit 최대 조회 수
     * @return 재확인할 예약 ID
     */
    List<UUID> findPendingIds(int limit);
}