package com.example.rental.reservation.domain.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

/**
 * 차량을 인수하고 반납하는 지점을 나타내는 JPA 엔티티다.
 */
@Entity
public class Branch {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;

    // JPA가 DB에서 읽은 값으로 객체를 만들 때 사용하는 생성자다.
    protected Branch() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}