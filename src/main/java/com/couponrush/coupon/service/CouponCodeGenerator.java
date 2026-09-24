package com.couponrush.coupon.service;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CouponCodeGenerator {

    private static final int CODE_LENGTH = 12;

    public String generate() {
        return UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, CODE_LENGTH)
                .toUpperCase();
    }
}
