package com.example.rental.web.controller;

import com.example.rental.web.client.ReservationClient;
import com.example.rental.web.dto.search.SearchForm;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** 차량 선점 버튼과 예약 상세 화면의 요청을 처리한다. */
@Controller
public class ReservationPageController {
    private final ReservationClient client;

    /**
     * Reservation API 클라이언트를 받는다.
     *
     * @param client 예약 API 클라이언트
     */
    public ReservationPageController(ReservationClient client) {
        this.client = client;
    }

    /**
     * 입력을 검사하고 차량 선점을 요청한다.
     *
     * @param form 화면에서 선택한 지점과 기간
     * @param binding 날짜·숫자 입력 변환 오류
     * @param vehicleId 선택한 차량
     * @param requestKey 화면이 발급한 요청 키
     * @param model 오류 안내와 재시도에 사용할 값
     * @param response 오류 상태를 설정할 HTTP 응답
     * @return 성공하면 상세 화면으로 이동하고 실패하면 안내 화면을 표시한다.
     */
    @PostMapping("/reservations")
    public String hold(
            @ModelAttribute("form") SearchForm form,
            BindingResult binding,
            @RequestParam Long vehicleId,
            @RequestParam UUID requestKey,
            Model model,
            HttpServletResponse response
    ) {
        model.addAttribute("vehicleId", vehicleId);
        model.addAttribute("requestKey", requestKey);
        model.addAttribute("retryAllowed", false);
        if (binding.hasErrors() || form.getBranchId() == null || form.getBranchId() <= 0
                || vehicleId <= 0 || form.getPickupAt() == null || form.getReturnAt() == null
                || !form.getReturnAt().isAfter(form.getPickupAt())) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("error", "지점, 차량과 인수·반납 시각을 확인해 주세요.");
            return "reservation-error";
        }
        try {
            var reservation = client.hold(
                    requestKey,
                    form.getBranchId(),
                    vehicleId,
                    form.getPickupAt(),
                    form.getReturnAt()
            );
            // 새로고침 시 POST가 반복되지 않도록 예약 상세 GET으로 이동한다.
            return "redirect:/reservations/" + reservation.id();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().is4xxClientError()) {
                response.setStatus(exception.getStatusCode().value());
                model.addAttribute("error", "선택한 조건으로 예약할 수 없습니다. 차량 목록을 다시 확인해 주세요.");
            } else {
                requestUncertain(model, response);
            }
        } catch (RestClientException exception) {
            requestUncertain(model, response);
        }
        return "reservation-error";
    }

    /**
     * 예약 상세를 조회해 지정한 형식으로 표시한다.
     *
     * @param id 예약 ID
     * @param model 예약 정보를 담을 화면 모델
     * @param response 오류 상태를 설정할 응답
     * @return 상세 또는 오류 안내 템플릿
     */
    @GetMapping("/reservations/{id}")
    public String detail(@PathVariable UUID id, Model model, HttpServletResponse response) {
        model.addAttribute("retryAllowed", false);
        try {
            model.addAttribute("reservation", client.findReservation(id));
            return "reservation";
        } catch (RestClientResponseException exception) {
            int status = exception.getStatusCode().is4xxClientError()
                    ? exception.getStatusCode().value() : HttpStatus.SERVICE_UNAVAILABLE.value();
            response.setStatus(status);
        } catch (RestClientException exception) {
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
        model.addAttribute("error", "예약 정보를 불러오지 못했습니다. 잠시 후 다시 확인해 주세요.");
        return "reservation-error";
    }

    /**
     * 요청 결과를 확인하지 못한 경우 같은 요청 키로 재시도하도록 안내한다.
     *
     * @param model 재시도 폼을 표시할 모델
     * @param response HTTP 503을 설정할 응답
     */
    private void requestUncertain(Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        model.addAttribute("error", "예약 결과를 확인하지 못했습니다. 같은 요청으로 다시 확인해 주세요.");
        // 서버에서 이미 저장했을 수 있으므로 같은 키와 조건으로 다시 요청한다.
        model.addAttribute("retryAllowed", true);
    }
}