package com.example.shop.dto;

import com.example.shop.domain.Member;
import com.example.shop.domain.MemberGrade;
import lombok.Getter;

@Getter
public class MyPointResponse {

    private final String name;
    private final MemberGrade grade;
    private final int point;

    public MyPointResponse(Member member) {
        this.name = member.getName();
        this.grade = member.getGrade();
        this.point = member.getPoint();
    }
}
