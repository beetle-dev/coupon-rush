package com.couponrush.event.controller;

import com.couponrush.event.dto.EventCreateRequest;
import com.couponrush.event.dto.EventResponse;
import com.couponrush.event.service.EventService;
import com.couponrush.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @MockitoBean
    EventService eventService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    String EVENT_NAME = "테스트 이벤트";
    int TOTAL_QUANTITY = 100;

    @Test
    void 이벤트_생성_성공시_201_응답() throws Exception {

        LocalDateTime now = LocalDateTime.now();
        var request = new EventCreateRequest(
                EVENT_NAME,
                TOTAL_QUANTITY,
                now,
                now.plusMonths(1)
        );

        given(eventService.createEvent(any()))
                .willReturn(new EventResponse(1L, "생성된 이벤트", 100, 100, LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("1"));
    }

    @Test
    void name이_비어있으면_400_응답() throws Exception {

        LocalDateTime now = LocalDateTime.now();
        var request = new EventCreateRequest(
                null,
                TOTAL_QUANTITY,
                now,
                now.plusMonths(1)
        );

        mockMvc.perform(post("/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 이벤트_조회_성공시_200과_이벤트_반환() throws Exception {

        given(eventService.getEvent(any(Long.class)))
                .willReturn(TestFixtures.createEventResponse());

        mockMvc.perform(get("/events/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.name").value("이벤트명"));
    }

    @Test
    void id가_비어있으면_400_응답() throws Exception {

        mockMvc.perform(get("/events"))
                .andExpect(status().isMethodNotAllowed());
    }

}