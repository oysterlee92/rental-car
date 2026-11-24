package com.example.rental.reservation.application.service;

import com.example.rental.reservation.application.dto.reservation.HoldResult;
import com.example.rental.reservation.application.dto.reservation.ReservationResponse;
import com.example.rental.reservation.application.transaction.ReservationTransactions;
import com.example.rental.reservation.domain.exception.ReservationConflictException;
import com.example.rental.reservation.domain.vo.BranchId;
import com.example.rental.reservation.domain.vo.RentalPeriod;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 선점 생성과 충돌 후 재조회 흐름을 조정한다. */
@Service
public class ReservationService {
    private final ReservationExpiryService expiry;
    private final ReservationTransactions transactions;

    /**
     * 서로 다른 트랜잭션을 실행할 서비스들을 받는다.
     *
     * @param expiry 만료 해제 서비스
     * @param transactions 예약 트랜잭션 서비스
     */
    public ReservationService(ReservationExpiryService expiry, ReservationTransactions transactions) {
        this.expiry = expiry;
        this.transactions = transactions;
    }

    /**
     * 만료된 선점을 정리한 뒤 차량을 선점한다.
     *
     * @param key 같은 요청을 구분할 키
     * @param branchId 지점
     * @param vehicleId 차량 ID
     * @param period 대여 기간
     * @return 생성 또는 재전송 결과
     * @throws ReservationConflictException 다른 예약이나 요청 조건이 충돌한 경우
     * @throws DataIntegrityViolationException 기존 요청으로 복구할 수 없는 DB 무결성 오류인 경우
     */
    public HoldResult hold(
            UUID key,
            BranchId branchId,
            long vehicleId,
            RentalPeriod period
    ) {
        // 이 호출이 커밋된 후 새 예약을 만든다. 저장 충돌로 만료 해제까지 취소되지 않는다.
        expiry.expireVehicle(vehicleId);
        try {
            return transactions.hold(
                    key,
                    branchId,
                    vehicleId,
                    period
            );
        } catch (ReservationConflictException | DataIntegrityViolationException exception) {
            // 같은 키가 동시에 INSERT되면 UNIQUE나 기간 제약 중 어느 쪽이 먼저 충돌할 수 있다.
            // 실패한 트랜잭션이 끝난 뒤 키를 다시 조회해 같은 요청이면 기존 예약을 반환한다.
            // 기존 요청이 없으면 원래 오류를 전달한다. 일반 DB 오류는 409로 바꾸지 않는다.
            var replay = transactions.replay(
                    key,
                    branchId,
                    vehicleId,
                    period
            );
            if (replay.isPresent()) {
                return new HoldResult(replay.get(), false);
            }
            throw exception;
        }
    }

    /**
     * 현재 예약 상태를 조회한다.
     *
     * @param id 예약 ID
     * @return 예약 정보
     */
    public ReservationResponse find(UUID id) {
        return transactions.find(id);
    }
}