package com.example.shop.controller;

import com.example.shop.domain.Member;
import com.example.shop.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 내 포인트 조회 API 코드 리뷰 지적 확인용 테스트. 시드 회원(DataInitializer)을 사용한다. */
@SpringBootTest
@AutoConfigureMockMvc
class MemberControllerMyPointTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;

    private Long seedId(String email) {
        return memberRepository.findByEmail(email).orElseThrow().getId();
    }

    // 지적 1: 헤더 없이도 Member 엔티티가 그대로 노출된다 (현재 동작을 단언)
    @Test
    void 지적1_회원조회는_헤더없이_200이고_email_phone이_노출된다() throws Exception {
        Long id = seedId("minjun@example.com");

        mockMvc.perform(get("/api/members/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("minjun@example.com"))
                .andExpect(jsonPath("$.phone").value("010-1111-2222"));
    }

    // 지적 2: X-MEMBER-ID 미검증 - 임의의 다른 회원 id로 그 회원의 정보가 조회된다 (특성화)
    @Test
    void 지적2_다른_회원_id를_넣어도_그_회원의_포인트가_조회된다() throws Exception {
        Long otherId = seedId("sua@example.com");

        mockMvc.perform(get("/api/members/me/point").header("X-MEMBER-ID", otherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("최수아"))
                .andExpect(jsonPath("$.data.grade").value("VIP"))
                .andExpect(jsonPath("$.data.point").value(30000));
    }

    // 지적 3-a: 헤더 누락은 GlobalExceptionHandler 가 처리하지 않아 ApiResponse 가 아닌 기본 Spring 응답(본문 없음)이 나간다. 지적 입증.
    @Test
    void 지적3_헤더_누락시_400이지만_ApiResponse_본문이_없다() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/members/me/point")).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("E400");
        assertThat(result.getResponse().getContentAsString()).isEmpty();
    }

    // 지적 3-b: X-MEMBER-ID: abc 는 변환 중 NumberFormatException(IllegalArgumentException 의 하위)이 발생해
    // 기존 핸들러가 잡아 E400 ApiResponse 로 나간다. 지적과 달리 형식은 맞다(지적 아님).
    // 다만 message 에 'For input string: "abc"' 같은 내부 예외 메시지가 그대로 노출된다.
    @Test
    void 지적3_헤더가_abc면_E400_ApiResponse로_응답된다() throws Exception {
        mockMvc.perform(get("/api/members/me/point").header("X-MEMBER-ID", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("E400"))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.message").value("For input string: \"abc\""));
    }

    // 지적 4: 정상 호출, /me/point 가 /{id} 와 충돌하지 않는다
    @Test
    void 지적4_시드_회원_포인트_조회는_200이고_email_phone이_없다() throws Exception {
        Long id = seedId("jiho@example.com");

        mockMvc.perform(get("/api/members/me/point").header("X-MEMBER-ID", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.name").value("박지호"))
                .andExpect(jsonPath("$.data.grade").value("GOLD"))
                .andExpect(jsonPath("$.data.point").value(12000))
                .andExpect(jsonPath("$.data.email").doesNotExist())
                .andExpect(jsonPath("$.data.phone").doesNotExist());
    }

    // 지적 5: grade 미지정 회원은 grade 가 null (현재 동작을 단언). 커밋하지 않도록 롤백용으로 직접 삭제한다.
    @Test
    void 지적5_grade_없이_저장한_회원은_grade가_null이다() throws Exception {
        Member member = new Member();
        member.setName("등급없음");
        member.setEmail("nograde-test@example.com");
        member.setPoint(10);
        Long id = memberRepository.save(member).getId();
        try {
            mockMvc.perform(get("/api/members/me/point").header("X-MEMBER-ID", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("등급없음"))
                    .andExpect(jsonPath("$.data.grade").doesNotExist());
        } finally {
            memberRepository.deleteById(id);
        }
    }
}
