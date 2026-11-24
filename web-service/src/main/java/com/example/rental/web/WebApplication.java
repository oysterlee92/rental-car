package com.example.rental.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Thymeleaf 검색 화면을 제공하는 독립 Web 애플리케이션의 진입점이다.
 */
@SpringBootApplication
public class WebApplication {
    /**
     * Web 애플리케이션을 시작한다.
     *
     * @param args Spring Boot에 전달할 실행 인수
     */
    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}