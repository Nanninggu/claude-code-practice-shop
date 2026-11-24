package com.example.shop.util;

public class Constants {

    // PG 연동 정보 (비밀 키와 관리자 토큰은 환경 변수로 주입: AppSecrets)
    public static final String PG_MERCHANT_ID = "shopnow_m_01";

    // 등급별 무료배송 기준 (등급 할인과 쿠폰을 뺀 금액이 이 값 이상이면 무료, VIP 는 항상 무료)
    public static final int FREE_SHIPPING_THRESHOLD_BRONZE = 50000;
    public static final int FREE_SHIPPING_THRESHOLD_SILVER = 40000;
    public static final int FREE_SHIPPING_THRESHOLD_GOLD = 30000;
    public static final int SHIPPING_FEE = 3000;

    public static final int MIN_USE_POINT = 1000;
    public static final int MAX_ORDER_QUANTITY = 99;
}
