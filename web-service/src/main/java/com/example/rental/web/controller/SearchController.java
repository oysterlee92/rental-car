package com.example.rental.web.controller;

import com.example.rental.web.client.ReservationClient;
import com.example.rental.web.dto.search.SearchForm;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 검색 입력을 검사하고 차량 조회 결과를 Thymeleaf 화면에 전달한다.
 */
@Controller
public class SearchController {
    private final ReservationClient reservation;

    /**
     * 지점·차량 조회 API를 호출할 클라이언트를 주입받는다.
     *
     * @param reservation Reservation HTTP 클라이언트
     */
    public SearchController(ReservationClient reservation) {
        this.reservation = reservation;
    }

    /**
     * 첫 화면을 표시하거나 입력한 조건으로 차량을 검색한다.
     *
     * @param form 검색 조건과 첫 화면의 기본값
     * @param binding 입력값을 숫자나 날짜로 바꾸지 못했을 때의 오류
     * @param search 사용자가 검색을 요청했는지 여부
     * @param model 지점, 차량, 오류 안내를 담을 화면 모델
     * @param response 입력 오류나 API 장애에 맞는 상태를 설정할 응답
     * @return 렌더링할 search 템플릿 이름
     */
    @GetMapping("/")
    public String search(
            @ModelAttribute("form") SearchForm form,
            BindingResult binding,
            @RequestParam(defaultValue = "false") boolean search,
            Model model,
            HttpServletResponse response
    ) {
        try {
            // 입력 오류가 있어도 지점 선택 목록을 다시 보여 준다.
            model.addAttribute("branches", reservation.branches());
            if (!search) {
                // 첫 방문에는 기본 입력 폼을 보여 주고 차량 조회는 검색 후 수행한다.
                return "search";
            }
            // 입력이 비었거나 숫자·날짜 변환에 실패했다면 시각을 비교하기 전에 오류를 안내한다.
            if (binding.hasErrors() || form.getBranchId() == null || form.getBranchId() <= 0
                    || form.getPickupAt() == null || form.getReturnAt() == null
                    || !form.getReturnAt().isAfter(form.getPickupAt())) {
                response.setStatus(HttpStatus.BAD_REQUEST.value());
                model.addAttribute("error", "지점과 인수·반납 시각을 확인해 주세요. 반납 시각은 인수 시각보다 늦어야 합니다.");
                return "search";
            }
            // SearchForm의 인수·반납 시각을 그대로 API에 전달한다.
            var result = reservation.search(form.getBranchId(), form.getPickupAt(), form.getReturnAt());
            model.addAttribute("vehicles", result.vehicles());
            // 차량마다 별도 요청 키를 발급하고 선점 폼의 숨은 입력에 넣는다.
            Map<Long, UUID> requestKeys = new HashMap<>();
            for (var vehicle : result.vehicles()) {
                requestKeys.put(vehicle.id(), UUID.randomUUID());
            }
            model.addAttribute("requestKeys", requestKeys);
        } catch (RestClientResponseException exception) {
            // 4xx 응답이면 검색 조건을 확인하도록, 5xx 응답이면 잠시 뒤 재시도하도록 안내한다.
            if (exception.getStatusCode().is4xxClientError()) {
                response.setStatus(exception.getStatusCode().value());
                model.addAttribute("error", "검색 조건과 선택한 지점을 확인해 주세요.");
            } else {
                unavailable(model, response);
            }
        } catch (RestClientException exception) {
            // Reservation에 연결하지 못하거나 응답을 기다리다 시간이 초과되면 재시도를 안내한다.
            unavailable(model, response);
        }
        return "search";
    }

    /**
     * API 연결 실패와 서버 오류에 공통으로 사용할 화면 안내를 설정한다.
     *
     * @param model 오류 메시지를 전달할 화면 모델
     * @param response HTTP 503을 설정할 응답
     */
    private void unavailable(Model model, HttpServletResponse response) {
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        model.addAttribute("error", "차량 정보를 불러오지 못했습니다. 잠시 후 다시 검색해 주세요.");
    }
}