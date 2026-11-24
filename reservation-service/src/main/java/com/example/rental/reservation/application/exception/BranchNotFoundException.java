package com.example.rental.reservation.application.exception;

import com.example.rental.reservation.domain.vo.BranchId;

/**
 * 검색한 지점 ID가 DB에 없을 때 발생하는 예외다.
 */
public class BranchNotFoundException extends RuntimeException {
    /**
     * 오류가 발생한 지점 ID로 메시지를 만든다.
     *
     * @param branchId 조회한 지점 ID
     */
    public BranchNotFoundException(BranchId branchId) {
        super("존재하지 않는 지점입니다. 지점 ID=" + branchId.value());
    }
}