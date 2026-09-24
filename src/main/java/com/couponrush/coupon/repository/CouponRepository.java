package com.couponrush.coupon.repository;

import com.couponrush.coupon.domain.Coupon;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {
    Optional<Coupon> findByEventIdAndUserId(Long eventId, Long userId);

    @Query("SELECT c.userId FROM Coupon c WHERE c.eventId = :eventId")
    List<Long> findUserIdsByEventId(@Param("eventId") Long eventId);
}
