package com.example.shop.config;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

/**
 * 환경 변수에서 읽는 비밀 값. 시작 시점에 없으면 이유를 알리고 기동을 중단한다.
 * 값은 로그나 예외 메시지에 절대 포함하지 않는다.
 */
@Component
public class AppSecrets {

    public static final String PG_SECRET_KEY_ENV = "PG_SECRET_KEY";
    public static final String ADMIN_TOKEN_ENV = "ADMIN_TOKEN";

    private final String pgSecretKey;
    private final String adminToken;

    public AppSecrets(Environment env) {
        String pg = env.getProperty(PG_SECRET_KEY_ENV);
        String admin = env.getProperty(ADMIN_TOKEN_ENV);

        List<String> missing = new ArrayList<>();
        if (pg == null || pg.isBlank()) {
            missing.add(PG_SECRET_KEY_ENV);
        }
        if (admin == null || admin.isBlank()) {
            missing.add(ADMIN_TOKEN_ENV);
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "필수 환경 변수가 설정되지 않아 시작할 수 없습니다: " + String.join(", ", missing)
                            + " (.env.example 참고, 값은 코드나 설정 파일이 아닌 환경 변수로만 주입하세요)");
        }
        this.pgSecretKey = pg;
        this.adminToken = admin;
    }

    public String pgSecretKey() {
        return pgSecretKey;
    }

    public boolean matchesAdminToken(String candidate) {
        if (candidate == null) {
            return false;
        }
        return MessageDigest.isEqual(
                adminToken.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8));
    }
}
