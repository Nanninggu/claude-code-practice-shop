package com.example.shop.service;

import com.example.shop.domain.*;
import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.util.PriceUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 특성 테스트(characterization test): 지금 코드가 내는 값을 그대로 고정한다.
 * 기대값이 "옳은 값"이라는 뜻이 아니다. 이상한 동작은 [QUIRK] 로 표시했다.
 *
 * 비교 대상 두 경로
 *  - 주문 시: OrderService 의 private calculateDiscount / calculateShippingFee
 *      할인 = 정수 나눗셈(내림), 무료배송 = 금액 >= 50,000
 *  - 결제 검증 시: PriceUtil.calculatePayPrice (PaymentService.pay 가 사용)
 *      할인 = Math.round(반올림),  무료배송 = 금액 >  50,000
 *
 * 두 경로의 규칙이 달라서, 경계값에서는 주문 시 저장된 payPrice 와 결제 검증 값이 어긋나
 * pay() 가 "결제 금액이 일치하지 않습니다." 로 실패한다.
 */
@SpringBootTest
@Transactional
class OrderPriceCharacterizationTest {

    private static final String MISMATCH_MESSAGE = "결제 금액이 일치하지 않습니다.";

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;

    // ---------------------------------------------------------------- 정상 구간 (두 경로 일치)

    @Test
    void 브론즈_10000원_할인없음_배송비3000_두경로_일치() {
        Order order = placeOrder(MemberGrade.BRONZE, 10000);
        assertOrder(order, 10000, 0, 3000, 13000);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(13000);
        assertPaymentAccepted(order);
    }

    @Test
    void 실버_10000원_3퍼센트할인_두경로_일치() {
        Order order = placeOrder(MemberGrade.SILVER, 10000);
        assertOrder(order, 10000, 300, 3000, 12700);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(12700);
        assertPaymentAccepted(order);
    }

    @Test
    void 골드_10000원_5퍼센트할인_두경로_일치() {
        Order order = placeOrder(MemberGrade.GOLD, 10000);
        assertOrder(order, 10000, 500, 3000, 12500);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(12500);
        assertPaymentAccepted(order);
    }

    @Test
    void VIP_10000원_10퍼센트할인_배송비항상0_두경로_일치() {
        Order order = placeOrder(MemberGrade.VIP, 10000);
        assertOrder(order, 10000, 1000, 0, 9000);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(9000);
        assertPaymentAccepted(order);
    }

    @Test
    void 브론즈_49999원_배송비3000_두경로_일치() {
        Order order = placeOrder(MemberGrade.BRONZE, 49999);
        assertOrder(order, 49999, 0, 3000, 52999);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(52999);
        assertPaymentAccepted(order);
    }

    @Test
    void 브론즈_50001원_무료배송_두경로_일치() {
        Order order = placeOrder(MemberGrade.BRONZE, 50001);
        assertOrder(order, 50001, 0, 0, 50001);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(50001);
        assertPaymentAccepted(order);
    }

    @Test
    void 실버_51500원_할인후_49955원이라_배송비3000_두경로_일치() {
        // 무료배송 기준은 할인 "후" 금액으로 판단한다
        Order order = placeOrder(MemberGrade.SILVER, 51500);
        assertOrder(order, 51500, 1545, 3000, 52955);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(52955);
        assertPaymentAccepted(order);
    }

    @Test
    void 실버_51600원_할인후_50052원이라_무료배송_두경로_일치() {
        Order order = placeOrder(MemberGrade.SILVER, 51600);
        assertOrder(order, 51600, 1548, 0, 50052);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(50052);
        assertPaymentAccepted(order);
    }

    // ---------------------------------------------------------------- [QUIRK] 두 경로가 어긋나는 구간

    @Test
    void QUIRK_브론즈_정확히_50000원_주문시는_무료배송_결제검증은_배송비3000_결제실패() {
        // 주문: amount >= 50000 이라 무료. PriceUtil: amount > 50000 이라 3000 부과.
        Order order = placeOrder(MemberGrade.BRONZE, 50000);
        assertOrder(order, 50000, 0, 0, 50000);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(53000);
        assertPaymentRejectedAsMismatch(order);
    }

    @Test
    void QUIRK_실버_1050원_할인_31점5원_주문시는_내림31_결제검증은_반올림32_결제실패() {
        // 주문: 1050*3/100 = 31 (내림) -> 4019. PriceUtil: round(31.5) = 32 -> 4018.
        Order order = placeOrder(MemberGrade.SILVER, 1050);
        assertOrder(order, 1050, 31, 3000, 4019);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(4018);
        assertPaymentRejectedAsMismatch(order);
    }

    @Test
    void QUIRK_VIP_1005원_할인_100점5원_주문시는_내림100_결제검증은_반올림101_결제실패() {
        Order order = placeOrder(MemberGrade.VIP, 1005);
        assertOrder(order, 1005, 100, 0, 905);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(904);
        assertPaymentRejectedAsMismatch(order);
    }

    @Test
    void QUIRK_골드_52631원_반올림때문에_할인후_금액이_50000원_경계를_가름_결제실패() {
        // 주문: 할인 2631(내림) -> 50000 -> >= 이라 무료 -> 50000.
        // PriceUtil: 할인 2632(반올림) -> 49999 -> 배송비 3000 -> 52999.
        Order order = placeOrder(MemberGrade.GOLD, 52631);
        assertOrder(order, 52631, 2631, 0, 50000);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(52999);
        assertPaymentRejectedAsMismatch(order);
    }

    @Test
    void QUIRK_골드_52632원_할인과_배송비_두가지가_모두_어긋남_결제실패() {
        // 주문: 할인 2631 -> 50001 -> 무료 -> 50001.
        // PriceUtil: 할인 2632 -> 50000 -> '>' 라 배송비 3000 -> 53000.
        Order order = placeOrder(MemberGrade.GOLD, 52632);
        assertOrder(order, 52632, 2631, 0, 50001);
        assertThat(PriceUtil.calculatePayPrice(order)).isEqualTo(53000);
        assertPaymentRejectedAsMismatch(order);
    }

    // ---------------------------------------------------------------- helpers

    /** 단가 unitPrice 짜리 상품 1개를 해당 등급 회원이 주문한다 (쿠폰/포인트 없음). */
    private Order placeOrder(MemberGrade grade, int unitPrice) {
        Member member = new Member();
        member.setEmail(UUID.randomUUID() + "@test.example");
        member.setName("테스터");
        member.setPhone("010-0000-0000");
        member.setGrade(grade);
        member.setPoint(0);
        member.setCreatedAt(LocalDateTime.now());
        memberRepository.save(member);

        Product product = new Product();
        product.setName("특성테스트 상품");
        product.setCategory("test");
        product.setPrice(unitPrice);
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        OrderCreateRequest.Item item = new OrderCreateRequest.Item();
        item.setProductId(product.getId());
        item.setQuantity(1);

        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(item));
        req.setUsePoint(0);
        req.setReceiverName("홍길동");
        req.setReceiverPhone("010-1234-5678");
        req.setAddress("서울시 어딘가 1");

        Long orderId = orderService.createOrder(member.getId(), req).getOrderId();
        return orderRepository.findById(orderId).orElseThrow();
    }

    private void assertOrder(Order order, int total, int discount, int shipping, int pay) {
        assertThat(order.getTotalPrice()).as("totalPrice").isEqualTo(total);
        assertThat(order.getDiscountPrice()).as("discountPrice").isEqualTo(discount);
        assertThat(order.getCouponDiscount()).as("couponDiscount").isZero();
        assertThat(order.getShippingFee()).as("shippingFee").isEqualTo(shipping);
        assertThat(order.getUsedPoint()).as("usedPoint").isZero();
        assertThat(order.getPayPrice()).as("payPrice").isEqualTo(pay);
    }

    private void assertPaymentAccepted(Order order) {
        Payment payment = paymentService.pay(order.getId(), "CARD", "1234-5678");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(payment.getAmount()).isEqualTo(order.getPayPrice());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    private void assertPaymentRejectedAsMismatch(Order order) {
        assertThatThrownBy(() -> paymentService.pay(order.getId(), "CARD", "1234-5678"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(MISMATCH_MESSAGE);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CREATED);
    }
}
