package com.example.rental.reservation.infrastructure.repository.adapter;

import com.example.rental.reservation.domain.entity.Reservation;
import com.example.rental.reservation.domain.enums.ReservationStatus;
import com.example.rental.reservation.domain.exception.ReservationConflictException;
import com.example.rental.reservation.domain.repository.ReservationRepository;
import com.example.rental.reservation.infrastructure.repository.jpa.JpaReservationRepository;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

/** 예약 저장과 잠금 조회를 JPA에 전달한다. */
@Repository
public class ReservationRepositoryAdapter implements ReservationRepository {
    private final JpaReservationRepository jpa;

    /**
     * JPA 예약 저장소를 받는다.
     *
     * @param jpa 예약 저장소 구현체
     */
    public ReservationRepositoryAdapter(JpaReservationRepository jpa) {
        this.jpa = jpa;
    }

    /**
     * DB에 INSERT를 실행하고 기간 배제 제약 충돌을 예약 충돌로 바꾼다.
     *
     * @param reservation 저장할 예약
     * @return 저장된 예약
     * @throws ReservationConflictException 차량의 배정 기간이 겹치는 경우
     */
    @Override
    public Reservation saveAndFlush(Reservation reservation) {
        try {
            return jpa.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException exception) {
            // 23P01은 기간 배제 제약 충돌이다. 다른 DB 오류는 원래 예외로 전달한다.
            if (exception.getMostSpecificCause() instanceof SQLException sql
                    && "23P01".equals(sql.getSQLState())) {
                throw new ReservationConflictException("선택한 기간에 이미 선점된 차량입니다.");
            }
            throw exception;
        }
    }

    /**
     * 예약 ID로 잠금 조회한다.
     *
     * @param id 예약 ID
     * @return 예약
     */
    @Override
    public Optional<Reservation> findByIdForUpdate(UUID id) {
        return jpa.findOneById(id);
    }

    /**
     * 요청 키로 잠금 조회한다.
     *
     * @param requestKey 재전송 확인용 키
     * @return 기존 예약
     */
    @Override
    public Optional<Reservation> findByRequestKeyForUpdate(UUID requestKey) {
        return jpa.findByRequestKey(requestKey);
    }

    /**
     * 한 번에 처리할 만료 대상을 조회한다.
     *
     * @param now 현재 시각
     * @param limit 최대 처리 건수
     * @return 만료 대상 목록
     */
    @Override
    public List<Reservation> findDueForUpdate(LocalDateTime now, int limit) {
        return jpa.findByStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(ReservationStatus.HELD, now, PageRequest.of(0, limit));
    }

    /**
     * 선점할 차량의 만료 대상을 조회한다.
     *
     * @param vehicleId 차량 ID
     * @param now 현재 시각
     * @return 만료 대상 목록
     */
    @Override
    public List<Reservation> findDueForVehicleForUpdate(long vehicleId, LocalDateTime now) {
        return jpa.findByVehicleIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAtAscIdAsc(vehicleId, ReservationStatus.HELD, now);
    }

    /**
     * 결제 중 예약을 생성 시각과 ID 순서로 조회해 예약 ID 목록으로 반환한다.
     *
     * @param limit 최대 조회 건수
     * @return HTTP 결제 결과를 다시 확인할 예약 ID 목록
     */
    @Override
    public List<UUID> findPendingIds(int limit) {
        return jpa.findByStatusOrderByCreatedAtAscIdAsc(ReservationStatus.PAYMENT_PENDING, PageRequest.of(0, limit))
                .stream().map(Reservation::getId).toList();
    }
}