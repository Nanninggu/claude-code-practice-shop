package com.example.shop.service;

import com.example.shop.domain.Member;
import com.example.shop.dto.MyPointResponse;
import com.example.shop.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class MemberService {

    private final MemberRepository memberRepository;

    public Member getMember(Long id) {
        return memberRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("회원을 찾을 수 없습니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public MyPointResponse getMyPoint(Long memberId) {
        return new MyPointResponse(getMember(memberId));
    }

    public void addPoint(Long memberId, int point) {
        Member member = getMember(memberId);
        member.setPoint(member.getPoint() + point);
    }

    public void usePoint(Long memberId, int point) {
        Member member = getMember(memberId);
        if (member.getPoint() < point) {
            throw new IllegalStateException("포인트가 부족합니다.");
        }
        member.setPoint(member.getPoint() - point);
    }
}
