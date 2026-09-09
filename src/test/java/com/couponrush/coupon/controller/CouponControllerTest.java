package com.couponrush.coupon.controller;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.coupon.dto.CouponIssueResponse;
import com.couponrush.coupon.service.CouponService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CouponController.class)
class CouponControllerTest {

    @MockitoBean
    CouponService couponService;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void 쿠폰_발급_성공시_201_쿠폰ID_반환() throws Exception {

        given(couponService.issueCoupon(any(Long.class), any(Long.class)))
                .willReturn(new CouponIssueResponse(1L));

        mockMvc.perform(post("/events/1/coupons")
                        .header("X-User-Id", "2"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.couponId").value("1"));
    }
}