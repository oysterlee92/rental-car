package com.example.rental.web.dto.search;

import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 브라우저에서 보낸 지점과 인수·반납 시각을 담는다.
 * 입력한 인수·반납 시각을 그대로 API에 전달한다.
 */
public class SearchForm {
    private Long branchId = 1L;
    // datetime-local의 분 단위 입력 형식으로 받는다.
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime pickupAt = LocalDateTime.now()
            .plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime returnAt = pickupAt.plusDays(1);

    public Long getBranchId() {
        return branchId;
    }

    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }

    public LocalDateTime getPickupAt() {
        return pickupAt;
    }

    public void setPickupAt(LocalDateTime pickupAt) {
        this.pickupAt = pickupAt;
    }

    public LocalDateTime getReturnAt() {
        return returnAt;
    }

    public void setReturnAt(LocalDateTime returnAt) {
        this.returnAt = returnAt;
    }
}