package com.wuho.erroralert.repository;

import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.ErrorEvent;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ErrorEventRepository extends JpaRepository<ErrorEvent, Long>, JpaSpecificationExecutor<ErrorEvent> {

    Page<ErrorEvent> findByProject_IdOrderByOccurredAtDesc(Long projectId, Pageable pageable);

    @Query("""
            select e.occurredAt
            from ErrorEvent e
            where e.project.id = :projectId
              and e.occurredAt >= :from
              and e.occurredAt <= :to
            order by e.occurredAt asc
            """)
    List<Instant> findOccurredAtForTrend(
            @Param("projectId") Long projectId,
            @Param("from") Instant from,
            @Param("to") Instant to
    );

    @Query("""
            select e.occurredAt
            from ErrorEvent e
            where e.project.id = :projectId
              and e.errorCode = :errorCode
              and e.occurredAt >= :from
              and e.occurredAt <= :to
            order by e.occurredAt asc
            """)
    List<Instant> findOccurredAtForTrend(
            @Param("projectId") Long projectId,
            @Param("errorCode") ErrorCode errorCode,
            @Param("from") Instant from,
            @Param("to") Instant to
    );
}
