package com.scriptles.cabinet.user.repository;

import com.scriptles.cabinet.user.entity.UserMediaActivity;
import com.scriptles.cabinet.user.enums.ProfileActivityType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ConsumptionReportActivityRepository extends JpaRepository<UserMediaActivity, UUID> {
    @EntityGraph(attributePaths = "media")
    @Query("""
            select activity
            from UserMediaActivity activity
            where activity.user.id = :userId
              and activity.type in :types
              and activity.occurredOn between :fromDate and :toDate
            order by activity.occurredOn asc, activity.id asc
            """)
    List<UserMediaActivity> findConsumptionActivities(
            @Param("userId") UUID userId,
            @Param("types") Collection<ProfileActivityType> types,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @EntityGraph(attributePaths = "media")
    @Query("""
            select activity
            from UserMediaActivity activity
            where activity.user.id = :userId
              and activity.type in :types
            order by activity.occurredOn asc, activity.id asc
            """)
    List<UserMediaActivity> findAllConsumptionActivities(
            @Param("userId") UUID userId,
            @Param("types") Collection<ProfileActivityType> types
    );
}
