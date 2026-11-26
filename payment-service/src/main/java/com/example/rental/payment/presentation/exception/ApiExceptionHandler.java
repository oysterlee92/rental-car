package com.example.rental.payment.presentation.exception;

import com.example.rental.payment.domain.exception.PaymentConflictException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 결제 오류를 HTTP 응답으로 변환한다. */
@RestControllerAdvice
public class ApiExceptionHandler {
    /**
     * 결제 조건 불일치를 안내한다.
     * @param exception 동일 키의 다른 조건
     * @return HTTP 409 응답
     */
    @ExceptionHandler(PaymentConflictException.class)
    public ProblemDetail conflict(PaymentConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /**
     * 필수 입력이나 JSON 형식 오류를 안내한다.
     * @param exception 입력 오류
     * @return HTTP 400 응답
     */
    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ProblemDetail invalid(Exception exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "예약 ID와 결제 금액, 승인 기한을 확인해 주세요.");
    }
}