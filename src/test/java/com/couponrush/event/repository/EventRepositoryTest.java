package com.couponrush.event.repository;

import com.couponrush.event.domain.Event;
import com.couponrush.support.TestContainer;
import com.couponrush.support.TestFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@DataJpaTest
class EventRepositoryTest extends TestContainer {

    @Autowired
    EventRepository eventRepository;

    @Test
    void Event를_생성하면_id가_자동으로_생성된다() {

        Event event = TestFixtures.createNoIDEvent();

        Event saved = eventRepository.save(event);

        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void id로_Event를_조회한다() {

        Event event = eventRepository.save(TestFixtures.createNoIDEvent());

        Event found = eventRepository.findById(event.getId())
                .orElse(null);

        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("이벤트명");
    }
}