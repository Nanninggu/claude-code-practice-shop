package com.example.shop.external;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PgResponse {

    private boolean success;
    private String tid;
    private String errorCode;
    private String errorMessage;

    public static PgResponse success(String tid) {
        PgResponse res = new PgResponse();
        res.setSuccess(true);
        res.setTid(tid);
        return res;
    }

    public static PgResponse fail(String code, String message) {
        PgResponse res = new PgResponse();
        res.setSuccess(false);
        res.setErrorCode(code);
        res.setErrorMessage(message);
        return res;
    }
}
