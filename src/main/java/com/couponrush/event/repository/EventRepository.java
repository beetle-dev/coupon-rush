package com.couponrush.event.repository;

import com.couponrush.event.domain.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = "SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> selectForUpdate(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Event e SET e.remainingQuantity = e.remainingQuantity - 1 "
            + "WHERE e.id = :id AND e.remainingQuantity > 0")
    int decreaseRemainingQuantity(@Param("id") Long id);
}
