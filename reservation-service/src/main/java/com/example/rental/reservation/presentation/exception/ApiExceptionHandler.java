package com.example.rental.reservation.presentation.exception;

import com.example.rental.reservation.application.exception.BranchNotFoundException;
import com.example.rental.reservation.application.exception.PaymentUnavailableException;
import com.example.rental.reservation.application.exception.ReservationNotFoundException;
import com.example.rental.reservation.application.exception.VehicleNotFoundException;
import com.example.rental.reservation.domain.exception.ReservationConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 지점 조회 실패나 잘못된 입력을 HTTP 오류 응답으로 바꾼다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    /**
     * 지점이 없다는 업무 오류를 HTTP 404로 표현한다.
     *
     * @param exception 존재하지 않는 지점 오류
     * @return 지점 조회 실패 응답
     */
    @ExceptionHandler(BranchNotFoundException.class)
    public ProblemDetail handleMissingBranch(BranchNotFoundException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * 지점 ID나 대여 기간이 잘못됐다는 예외를 HTTP 400으로 안내한다.
     *
     * @param exception ID가 0 이하이거나 대여 기간이 잘못된 경우 발생한 예외
     * @return 입력 오류 응답
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleInvalidValue(IllegalArgumentException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    /**
     * 필수 입력 누락이나 날짜·숫자 변환 실패를 안내한다.
     *
     * @param exception 필수 입력이 없거나 숫자·날짜로 변환하지 못한 오류
     * @return 입력 형식 안내 응답
     */
    @ExceptionHandler({MissingRequestValueException.class, MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ProblemDetail handleInput(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "필수 입력과 요청 키, 날짜 형식을 확인해 주세요.");
    }

    /**
     * 차량 또는 예약이 없는 경우 HTTP 404로 안내한다.
     *
     * @param exception 조회 대상이 없다는 오류
     * @return 조회 실패 응답
     */
    @ExceptionHandler({ReservationNotFoundException.class, VehicleNotFoundException.class})
    public ProblemDetail handleMissingReservation(RuntimeException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    /**
     * 기간 충돌과 요청 키 재사용을 HTTP 409로 안내한다.
     *
     * @param exception 예약 조건이 충돌한 이유
     * @return 충돌 안내 응답
     */
    @ExceptionHandler(ReservationConflictException.class)
    public ProblemDetail handleConflict(ReservationConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
    /**
     * 결제 결과를 확인하지 못한 요청에 재확인을 안내한다.
     * @param exception 통신 또는 결제 응답 오류
     * @return 결과 미확인 응답
     */
    @ExceptionHandler(PaymentUnavailableException.class)
    public ProblemDetail handlePaymentUnavailable(PaymentUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }
}