package com.couponrush.event.service;

import com.couponrush.common.exception.CustomRuntimeException;
import com.couponrush.common.exception.ErrorCode;
import com.couponrush.event.domain.Event;
import com.couponrush.event.dto.EventCreateRequest;
import com.couponrush.event.dto.EventResponse;
import com.couponrush.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;

    @Transactional
    public EventResponse createEvent(EventCreateRequest request) {

        Event event = eventRepository.save(request.toEvent());
        log.info("이벤트 생성 완료. eventId={}", event.getId());

        return event.toEventResponse();
    }

    @Transactional(readOnly = true)
    public EventResponse getEvent(Long id) {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new CustomRuntimeException(ErrorCode.EVENT_NOT_FOUND));

        return event.toEventResponse();
    }

    @Transactional(readOnly = true)
    public Event getEventEntity(Long id) {

        return eventRepository.findById(id)
                .orElseThrow(() -> new CustomRuntimeException(ErrorCode.EVENT_NOT_FOUND));
    }

    @Transactional
    public Event findByIdForUpdate(Long id) {

        return eventRepository.selectForUpdate(id)
                .orElseThrow(() -> new CustomRuntimeException(ErrorCode.EVENT_NOT_FOUND));
    }

    @Transactional
    public void decreaseRemainingQty(Long eventId) {

        Event event = findByIdForUpdate(eventId);   // 비관적락 적용

        LocalDateTime now = LocalDateTime.now();
        if (event.getIssueStartAt().isAfter(now) || event.getIssueEndAt().isBefore(now)) {
            throw new CustomRuntimeException(ErrorCode.COUPON_INVALID_ISSUE_PERIOD);
        }

        event.decreaseRemainingQty();
    }
}
