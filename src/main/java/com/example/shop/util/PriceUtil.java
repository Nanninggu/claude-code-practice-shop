package com.example.shop.util;

import com.example.shop.domain.MemberGrade;
import com.example.shop.domain.Order;
import com.example.shop.domain.OrderItem;

/**
 * 금액 계산 유틸
 * 결제 검증 시 사용
 */
public class PriceUtil {

    public static int getDiscountRate(MemberGrade grade) {
        switch (grade) {
            case SILVER:
                return 3;
            case GOLD:
                return 5;
            case VIP:
                return 10;
            default:
                return 0;
        }
    }

    public static int calcDiscount(int totalPrice, MemberGrade grade) {
        int rate = getDiscountRate(grade);
        return (int) Math.round(totalPrice * rate / 100.0);
    }

    public static int calcShippingFee(int amount, MemberGrade grade) {
        if (grade == MemberGrade.VIP) {
            return 0;
        }
        if (amount > Constants.FREE_SHIPPING_THRESHOLD) {
            return 0;
        }
        return Constants.SHIPPING_FEE;
    }

    public static int calculatePayPrice(Order order) {
        int total = 0;
        for (OrderItem item : order.getItems()) {
            total += item.getUnitPrice() * item.getQuantity();
        }
        MemberGrade grade = order.getMember().getGrade();
        int discount = calcDiscount(total, grade);
        int coupon = order.getCouponDiscount();
        int shipping = calcShippingFee(total - discount - coupon, grade);
        return total - discount - coupon + shipping - order.getUsedPoint();
    }

    public static String formatPrice(int price) {
        return String.format("%,d원", price);
    }
}
