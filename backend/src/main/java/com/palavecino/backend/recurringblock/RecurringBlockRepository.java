package com.palavecino.backend.recurringblock;

import com.palavecino.backend.availability.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurringBlockRepository extends JpaRepository<RecurringBlock, Long> {

    @Query("""
            SELECT rb FROM RecurringBlock rb
            WHERE rb.active = true
              AND rb.dayOfWeek = :dayOfWeek
              AND rb.startTime < :rangeEnd
              AND rb.endTime > :rangeStart
            """)
    List<RecurringBlock> findActiveOverlapping(
            @Param("dayOfWeek") DayOfWeek dayOfWeek,
            @Param("rangeStart") LocalTime rangeStart,
            @Param("rangeEnd") LocalTime rangeEnd);
}
