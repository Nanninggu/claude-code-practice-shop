package com.example.shop.service;

import com.example.shop.domain.Member;
import com.example.shop.domain.MemberGrade;
import com.example.shop.dto.MyPointResponse;
import com.example.shop.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MemberServiceMyPointTest {

    @Autowired MemberService memberService;
    @Autowired MemberRepository memberRepository;

    @Test
    void 내_포인트_조회_성공() {
        Member member = new Member();
        member.setName("테스터");
        member.setEmail("t@example.com");
        member.setGrade(MemberGrade.GOLD);
        member.setPoint(1234);
        Long id = memberRepository.save(member).getId();

        MyPointResponse result = memberService.getMyPoint(id);

        assertThat(result.getName()).isEqualTo("테스터");
        assertThat(result.getGrade()).isEqualTo(MemberGrade.GOLD);
        assertThat(result.getPoint()).isEqualTo(1234);
    }

    @Test
    void 없는_회원이면_IllegalArgumentException() {
        assertThatThrownBy(() -> memberService.getMyPoint(987_654_321L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
