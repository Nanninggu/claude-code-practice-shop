package com.example.shop.service;

import com.example.shop.domain.Coupon;
import com.example.shop.domain.CouponType;
import com.example.shop.domain.Member;
import com.example.shop.domain.MemberGrade;
import com.example.shop.domain.Order;
import com.example.shop.domain.OrderStatus;
import com.example.shop.domain.Payment;
import com.example.shop.domain.PaymentStatus;
import com.example.shop.domain.Product;
import com.example.shop.domain.ProductStatus;
import com.example.shop.dto.OrderCreateRequest;
import com.example.shop.repository.CouponRepository;
import com.example.shop.repository.MemberRepository;
import com.example.shop.repository.OrderRepository;
import com.example.shop.repository.ProductRepository;
import com.example.shop.util.PriceUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 등급별 무료배송 기준 (요구사항)
 *
 *   판단 금액 = 상품 금액 - 등급 할인 - 쿠폰 할인
 *   브론즈 50,000원 이상 / 실버 40,000원 이상 / 골드 30,000원 이상 무료 (경계값 포함)
 *   VIP 는 금액과 관계없이 항상 무료. 그 외에는 배송비 3,000원.
 *   등급 할인율: 실버 3%, 골드 5%, VIP 10%.
 *
 * 기대값은 현재 코드를 돌려서 얻은 것이 아니라 위 규칙에서 손으로 계산했다.
 *
 * 주문 금액은 할인액이 원 단위로 떨어지거나, 주문 쪽 내림과 결제 검증 쪽 반올림이
 * 같은 값이 되는 것만 골랐다. 그래야 이 테스트가 배송비 규칙만 검증하고, 할인 반올림 차이로는 흔들리지 않는다.
 * 골드는 쿠폰 없이는 할인 후 금액을 정확히 30,000원으로 만드는 상품 금액이 없다
 * (31,578원은 내림/반올림이 갈려 결제 검증이 어긋난다). 그래서 골드의 정확한 경계는 정액 쿠폰으로 만든다.
 */
@SpringBootTest
@Transactional
class GradeFreeShippingThresholdTest {

    private static final String CARD = "1234-5678";

    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired MemberRepository memberRepository;
    @Autowired ProductRepository productRepository;
    @Autowired OrderRepository orderRepository;
    @Autowired CouponRepository couponRepository;

    // ================================================== 판정 함수 (경계 바로 아래 / 정확히 경계)

    @Test
    void 브론즈_49999원은_배송비_3000원_50000원은_0원() {
        assertThat(PriceUtil.calcShippingFee(49999, MemberGrade.BRONZE)).isEqualTo(3000);
        assertThat(PriceUtil.calcShippingFee(50000, MemberGrade.BRONZE)).isZero();
    }

    @Test
    void 실버_39999원은_배송비_3000원_40000원은_0원() {
        assertThat(PriceUtil.calcShippingFee(39999, MemberGrade.SILVER)).isEqualTo(3000);
        assertThat(PriceUtil.calcShippingFee(40000, MemberGrade.SILVER)).isZero();
    }

    @Test
    void 골드_29999원은_배송비_3000원_30000원은_0원() {
        assertThat(PriceUtil.calcShippingFee(29999, MemberGrade.GOLD)).isEqualTo(3000);
        assertThat(PriceUtil.calcShippingFee(30000, MemberGrade.GOLD)).isZero();
    }

    @Test
    void VIP는_금액과_관계없이_배송비_0원() {
        assertThat(PriceUtil.calcShippingFee(1, MemberGrade.VIP)).isZero();
        assertThat(PriceUtil.calcShippingFee(39999, MemberGrade.VIP)).isZero();
    }

    @Test
    void 등급_사이_구간은_등급마다_결과가_다르다() {
        // 45,000원: 브론즈 유료(< 50,000), 실버 무료(>= 40,000), 골드 무료(>= 30,000)
        assertThat(PriceUtil.calcShippingFee(45000, MemberGrade.BRONZE)).isEqualTo(3000);
        assertThat(PriceUtil.calcShippingFee(45000, MemberGrade.SILVER)).isZero();
        assertThat(PriceUtil.calcShippingFee(45000, MemberGrade.GOLD)).isZero();
        // 35,000원: 브론즈 유료, 실버 유료(< 40,000), 골드 무료
        assertThat(PriceUtil.calcShippingFee(35000, MemberGrade.BRONZE)).isEqualTo(3000);
        assertThat(PriceUtil.calcShippingFee(35000, MemberGrade.SILVER)).isEqualTo(3000);
        assertThat(PriceUtil.calcShippingFee(35000, MemberGrade.GOLD)).isZero();
    }

    // ================================================== 주문 → 결제 검증 → 결제 성공

    @Test
    void 브론즈_49999원_주문은_배송비_3000원_결제금액_52999원() {
        Order order = placeOrder(MemberGrade.BRONZE, 49999, 0);
        // 할인 0, 판단 금액 49,999 < 50,000 → 배송비 3,000 → 49,999 + 3,000
        assertOrderAndPay(order, 0, 0, 3000, 52999);
    }

    @Test
    void 브론즈_50000원_주문은_배송비_0원_결제금액_50000원() {
        Order order = placeOrder(MemberGrade.BRONZE, 50000, 0);
        assertOrderAndPay(order, 0, 0, 0, 50000);
    }

    @Test
    void 실버_상품_41236원은_할인후_39999원이라_배송비_3000원() {
        // 할인 41,236 × 3% = 1,237.08 → 1,237. 판단 금액 41,236 - 1,237 = 39,999 < 40,000
        Order order = placeOrder(MemberGrade.SILVER, 41236, 0);
        assertOrderAndPay(order, 1237, 0, 3000, 42999);
    }

    @Test
    void 실버_상품_41237원은_할인후_정확히_40000원이라_배송비_0원() {
        // 할인 41,237 × 3% = 1,237.11 → 1,237. 판단 금액 41,237 - 1,237 = 40,000 >= 40,000
        Order order = placeOrder(MemberGrade.SILVER, 41237, 0);
        assertOrderAndPay(order, 1237, 0, 0, 40000);
    }

    @Test
    void 실버_쿠폰까지_빼면_39999원이라_배송비_3000원() {
        // 50,000 - 1,500(3%) = 48,500 → 정액쿠폰 8,501 → 판단 금액 39,999 < 40,000
        Order order = placeOrder(MemberGrade.SILVER, 50000, 8501);
        assertOrderAndPay(order, 1500, 8501, 3000, 42999);
    }

    @Test
    void 실버_쿠폰까지_빼면_정확히_40000원이라_배송비_0원() {
        // 50,000 - 1,500(3%) = 48,500 → 정액쿠폰 8,500 → 판단 금액 40,000 >= 40,000
        Order order = placeOrder(MemberGrade.SILVER, 50000, 8500);
        assertOrderAndPay(order, 1500, 8500, 0, 40000);
    }

    @Test
    void 골드_상품_31560원은_할인후_29982원이라_배송비_3000원() {
        // 할인 31,560 × 5% = 1,578 (딱 떨어짐). 판단 금액 31,560 - 1,578 = 29,982 < 30,000
        Order order = placeOrder(MemberGrade.GOLD, 31560, 0);
        assertOrderAndPay(order, 1578, 0, 3000, 32982);
    }

    @Test
    void 골드_상품_31580원은_할인후_30001원이라_배송비_0원() {
        // 할인 31,580 × 5% = 1,579 (딱 떨어짐). 판단 금액 31,580 - 1,579 = 30,001 >= 30,000
        Order order = placeOrder(MemberGrade.GOLD, 31580, 0);
        assertOrderAndPay(order, 1579, 0, 0, 30001);
    }

    @Test
    void 골드_쿠폰까지_빼면_29999원이라_배송비_3000원() {
        // 40,000 - 2,000(5%) = 38,000 → 정액쿠폰 8,001 → 판단 금액 29,999 < 30,000
        Order order = placeOrder(MemberGrade.GOLD, 40000, 8001);
        assertOrderAndPay(order, 2000, 8001, 3000, 32999);
    }

    @Test
    void 골드_쿠폰까지_빼면_정확히_30000원이라_배송비_0원() {
        // 40,000 - 2,000(5%) = 38,000 → 정액쿠폰 8,000 → 판단 금액 30,000 >= 30,000
        Order order = placeOrder(MemberGrade.GOLD, 40000, 8000);
        assertOrderAndPay(order, 2000, 8000, 0, 30000);
    }

    @Test
    void VIP_1000원_주문도_배송비_0원() {
        // 할인 1,000 × 10% = 100 → 900원. 금액이 아무리 작아도 VIP 는 무료
        Order order = placeOrder(MemberGrade.VIP, 1000, 0);
        assertOrderAndPay(order, 100, 0, 0, 900);
    }

    // ================================================== 미리보기는 주문과 같은 배송비를 보여준다

    @Test
    void 미리보기_실버_41236원은_배송비_3000원_결제예정_42999원() {
        Map<String, Integer> r = preview(MemberGrade.SILVER, 41236, 0);
        assertThat(r.get("discountPrice")).isEqualTo(1237);
        assertThat(r.get("shippingFee")).isEqualTo(3000);
        assertThat(r.get("payPrice")).isEqualTo(42999);
    }

    @Test
    void 미리보기_실버_41237원은_배송비_0원_결제예정_40000원() {
        Map<String, Integer> r = preview(MemberGrade.SILVER, 41237, 0);
        assertThat(r.get("discountPrice")).isEqualTo(1237);
        assertThat(r.get("shippingFee")).isZero();
        assertThat(r.get("payPrice")).isEqualTo(40000);
    }

    @Test
    void 미리보기_골드_쿠폰_반영후_29999원은_배송비_3000원() {
        Map<String, Integer> r = preview(MemberGrade.GOLD, 40000, 8001);
        assertThat(r.get("couponDiscount")).isEqualTo(8001);
        assertThat(r.get("shippingFee")).isEqualTo(3000);
        assertThat(r.get("payPrice")).isEqualTo(32999);
    }

    @Test
    void 미리보기_골드_쿠폰_반영후_정확히_30000원은_배송비_0원() {
        Map<String, Integer> r = preview(MemberGrade.GOLD, 40000, 8000);
        assertThat(r.get("couponDiscount")).isEqualTo(8000);
        assertThat(r.get("shippingFee")).isZero();
        assertThat(r.get("payPrice")).isEqualTo(30000);
    }

    @Test
    void 미리보기_브론즈_49999원은_배송비_3000원_50000원은_0원() {
        assertThat(preview(MemberGrade.BRONZE, 49999, 0).get("shippingFee")).isEqualTo(3000);
        assertThat(preview(MemberGrade.BRONZE, 50000, 0).get("shippingFee")).isZero();
    }

    // ================================================== helpers

    /** 주문의 금액 구성이 기대와 같고, 결제 검증을 통과해 같은 금액으로 결제되는지 확인한다. */
    private void assertOrderAndPay(Order order, int discount, int coupon, int shipping, int pay) {
        assertThat(order.getDiscountPrice()).as("등급 할인").isEqualTo(discount);
        assertThat(order.getCouponDiscount()).as("쿠폰 할인").isEqualTo(coupon);
        assertThat(order.getShippingFee()).as("배송비").isEqualTo(shipping);
        assertThat(order.getPayPrice()).as("결제금액").isEqualTo(pay);

        Payment payment = paymentService.pay(order.getId(), "CARD", CARD);

        assertThat(payment.getAmount()).as("결제된 금액").isEqualTo(pay);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    /** 단가 unitPrice 상품 1개를 주문한다. fixedCoupon > 0 이면 그 금액의 정액 쿠폰을 쓴다. */
    private Order placeOrder(MemberGrade grade, int unitPrice, int fixedCoupon) {
        Member member = newMember(grade);
        OrderCreateRequest req = request(newProduct(unitPrice), fixedCoupon);
        Long orderId = orderService.createOrder(member.getId(), req).getOrderId();
        return orderRepository.findById(orderId).orElseThrow();
    }

    private Map<String, Integer> preview(MemberGrade grade, int unitPrice, int fixedCoupon) {
        Member member = newMember(grade);
        return orderService.previewPrice(member.getId(), request(newProduct(unitPrice), fixedCoupon));
    }

    private OrderCreateRequest request(Product product, int fixedCoupon) {
        OrderCreateRequest.Item item = new OrderCreateRequest.Item();
        item.setProductId(product.getId());
        item.setQuantity(1);

        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(item));
        req.setUsePoint(0);
        req.setReceiverName("홍길동");
        req.setReceiverPhone("010-1234-5678");
        req.setAddress("서울시 어딘가 1");
        if (fixedCoupon > 0) {
            req.setCouponCode(newFixedCoupon(fixedCoupon).getCode());
        }
        return req;
    }

    private Member newMember(MemberGrade grade) {
        Member member = new Member();
        member.setEmail(UUID.randomUUID() + "@test.example");
        member.setName("테스터");
        member.setPhone("010-0000-0000");
        member.setGrade(grade);
        member.setPoint(0);
        member.setCreatedAt(LocalDateTime.now());
        return memberRepository.save(member);
    }

    private Product newProduct(int price) {
        Product product = new Product();
        product.setName("등급별 무료배송 테스트 상품 " + price);
        product.setCategory("test");
        product.setPrice(price);
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        product.setCreatedAt(LocalDateTime.now());
        product.setUpdatedAt(LocalDateTime.now());
        return productRepository.save(product);
    }

    private Coupon newFixedCoupon(int amount) {
        Coupon coupon = new Coupon();
        coupon.setCode("T-" + UUID.randomUUID());
        coupon.setType(CouponType.FIXED);
        coupon.setDiscountValue(amount);
        coupon.setExpiresAt(LocalDate.now().plusDays(30));
        coupon.setUsed(false);
        return couponRepository.save(coupon);
    }
}
