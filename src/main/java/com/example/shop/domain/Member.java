package com.example.shop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String email;

    private String name;

    private String phone;

    @Enumerated(EnumType.STRING)
    private MemberGrade grade;

    // 보유 포인트
    private int point;

    private LocalDateTime createdAt;
}
