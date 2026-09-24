package com.couponrush.coupon.controller;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.dto.CouponIssueAcceptedResponse;
import com.couponrush.coupon.service.CouponIssueAsyncService;
import com.couponrush.coupon.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponController.class)
class CouponControllerTest {

    @MockitoBean
    CouponService couponService;
    @MockitoBean
    CouponIssueAsyncService couponIssueAsyncService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 쿠폰_발급_접수시_202_쿠폰코드_반환() throws Exception {

        given(couponIssueAsyncService.issueCoupon(any(Long.class), any(Long.class)))
                .willReturn(CouponIssueAcceptedResponse.pending("ABCDEF123456"));

        mockMvc.perform(post("/events/1/coupons")
                        .header("X-User-Id", "2"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/events/1/coupons?userId=2"))
                .andExpect(jsonPath("$.code").value("ABCDEF123456"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void 아직_영속화되지_않았으면_사용자_쿠폰_조회는_404() throws Exception {

        given(couponService.getCoupon(1L, 2L))
                .willThrow(new CustomRuntimeException(ErrorCode.COUPON_NOT_FOUND));

        mockMvc.perform(get("/events/1/coupons").param("userId", "2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.COUPON_NOT_FOUND.getCode()));
    }
}
