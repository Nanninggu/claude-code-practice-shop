package com.example.shop.controller;

import com.example.shop.common.ApiResponse;
import com.example.shop.domain.Member;
import com.example.shop.dto.MyPointResponse;
import com.example.shop.service.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/{id}")
    public Member getMember(@PathVariable Long id) {
        return memberService.getMember(id);
    }

    @GetMapping("/me/point")
    public ApiResponse<MyPointResponse> getMyPoint(@RequestHeader("X-MEMBER-ID") Long memberId) {
        return ApiResponse.ok(memberService.getMyPoint(memberId));
    }
}
