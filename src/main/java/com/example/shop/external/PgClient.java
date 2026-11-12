package com.example.shop.external;

public interface PgClient {

    PgResponse approve(String oid, int amount, String cardNo);

    PgResponse cancel(String tid, int amount);
}
