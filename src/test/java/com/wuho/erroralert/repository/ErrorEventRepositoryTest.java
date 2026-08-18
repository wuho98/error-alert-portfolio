package com.wuho.erroralert.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wuho.erroralert.config.JpaAuditingConfig;
import com.wuho.erroralert.domain.ErrorCode;
import com.wuho.erroralert.domain.ErrorEvent;
import com.wuho.erroralert.domain.Project;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("test")
@Import(JpaAuditingConfig.class)
class ErrorEventRepositoryTest {

    private static final ErrorCode ERROR_CODE = ErrorCode.PAYMENT_PG_TIMEOUT;
    private static final String ERROR_MESSAGE = "PG approval request timed out after 5000ms";
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-04T12:34:45Z");
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-04T12:34:46Z");

    @Autowired
    private ErrorEventRepository errorEventRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void savesErrorEventAndAssignsId() {
        Project project = saveProject("payment-service");
        ErrorEvent event = ErrorEvent.create(
                project,
                ERROR_CODE,
                ERROR_MESSAGE,
                OCCURRED_AT,
                RECEIVED_AT
        );

        ErrorEvent saved = errorEventRepository.saveAndFlush(event);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getProject().getId()).isEqualTo(project.getId());
        assertThat(saved.getErrorCode()).isEqualTo(ERROR_CODE);
        assertThat(saved.getMessage()).isEqualTo(ERROR_MESSAGE);
        assertThat(saved.getOccurredAt()).isEqualTo(OCCURRED_AT);
        assertThat(saved.getReceivedAt()).isEqualTo(RECEIVED_AT);
    }

    @Test
    void storesApiContractErrorCodeValueInDatabase() {
        ErrorEvent saved = errorEventRepository.saveAndFlush(createErrorEvent(ERROR_MESSAGE));

        // enum 상수명이 아니라 API/DB 계약값이 저장되는지 확인한다.
        String storedErrorCode = jdbcTemplate.queryForObject(
                "select error_code from errors where id = ?",
                String.class,
                saved.getId()
        );
        assertThat(storedErrorCode).isEqualTo("PAYMENT.PG_TIMEOUT");
    }

    @Test
    void savesMessageAtMaxLength() {
        String maxLengthMessage = "a".repeat(ErrorEvent.MESSAGE_MAX_LENGTH);

        ErrorEvent saved = errorEventRepository.saveAndFlush(createErrorEvent(maxLengthMessage));

        assertThat(saved.getMessage())
                .hasSize(ErrorEvent.MESSAGE_MAX_LENGTH)
                .isEqualTo(maxLengthMessage);
    }

    @Test
    void truncatesMessageOverMaxLength() {
        String tooLongMessage = "a".repeat(ErrorEvent.MESSAGE_MAX_LENGTH + 1);
        String expectedMessage = "a".repeat(ErrorEvent.MESSAGE_MAX_LENGTH - 3) + "...";

        ErrorEvent saved = errorEventRepository.saveAndFlush(createErrorEvent(tooLongMessage));

        assertThat(saved.getMessage())
                .hasSize(ErrorEvent.MESSAGE_MAX_LENGTH)
                .isEqualTo(expectedMessage)
                .endsWith("...");

        String storedMessage = jdbcTemplate.queryForObject(
                "select message from errors where id = ?",
                String.class,
                saved.getId()
        );
        assertThat(storedMessage).isEqualTo(expectedMessage);
    }

    @Test
    void findsErrorEventsByProjectIdOnly() {
        Project firstProject = saveProject("payment-service");
        Project otherProject = saveProject("order-service");
        ErrorEvent firstProjectOldEvent = ErrorEvent.create(
                firstProject,
                ErrorCode.PAYMENT_PG_TIMEOUT,
                "old timeout",
                Instant.parse("2026-08-04T12:34:45Z"),
                Instant.parse("2026-08-04T12:34:46Z")
        );
        ErrorEvent firstProjectNewEvent = ErrorEvent.create(
                firstProject,
                ErrorCode.PAYMENT_PG_SERVER_ERROR,
                "new timeout",
                Instant.parse("2026-08-04T12:35:45Z"),
                Instant.parse("2026-08-04T12:35:46Z")
        );
        ErrorEvent otherProjectEvent = ErrorEvent.create(
                otherProject,
                ErrorCode.PAYMENT_PG_TIMEOUT,
                "other project timeout",
                Instant.parse("2026-08-04T12:36:45Z"),
                Instant.parse("2026-08-04T12:36:46Z")
        );
        errorEventRepository.save(firstProjectOldEvent);
        errorEventRepository.save(firstProjectNewEvent);
        errorEventRepository.save(otherProjectEvent);
        errorEventRepository.flush();

        assertThat(errorEventRepository.findByProject_IdOrderByOccurredAtDesc(
                        firstProject.getId(),
                        PageRequest.of(0, 10)
                ).getContent())
                .extracting(ErrorEvent::getMessage)
                .containsExactly("new timeout", "old timeout");
    }

    @Test
    void pagedRecentProjectLookupExecutesContentAndCountQueries() {
        Project project = saveProject("payment-service");
        errorEventRepository.save(ErrorEvent.create(
                project,
                ErrorCode.PAYMENT_PG_TIMEOUT,
                "old timeout",
                Instant.parse("2026-08-04T12:34:45Z"),
                Instant.parse("2026-08-04T12:34:46Z")
        ));
        errorEventRepository.save(ErrorEvent.create(
                project,
                ErrorCode.PAYMENT_PG_SERVER_ERROR,
                "new timeout",
                Instant.parse("2026-08-04T12:35:45Z"),
                Instant.parse("2026-08-04T12:35:46Z")
        ));
        errorEventRepository.flush();
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();

        var page = errorEventRepository.findByProject_IdOrderByOccurredAtDesc(
                project.getId(),
                PageRequest.of(0, 1)
        );

        assertThat(page.getContent())
                .extracting(ErrorEvent::getMessage)
                .containsExactly("new timeout");
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void accessingProjectIdFromRecentResultDoesNotFetchProject() {
        Project project = saveProject("payment-service");
        errorEventRepository.saveAndFlush(ErrorEvent.create(
                project,
                ErrorCode.PAYMENT_PG_TIMEOUT,
                "timeout",
                OCCURRED_AT,
                RECEIVED_AT
        ));
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();
        ErrorEvent found = errorEventRepository.findByProject_IdOrderByOccurredAtDesc(
                project.getId(),
                PageRequest.of(0, 1)
        ).getContent().get(0);
        long queryCountAfterLookup = statistics.getPrepareStatementCount();

        Long projectId = found.getProject().getId();

        assertThat(projectId).isEqualTo(project.getId());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(queryCountAfterLookup);
    }

    @Test
    void accessingProjectFieldsFromRecentResultFetchesProjectLazily() {
        Project project = saveProject("payment-service");
        errorEventRepository.saveAndFlush(ErrorEvent.create(
                project,
                ErrorCode.PAYMENT_PG_TIMEOUT,
                "timeout",
                OCCURRED_AT,
                RECEIVED_AT
        ));
        entityManager.clear();
        Statistics statistics = statistics();
        statistics.clear();
        ErrorEvent found = errorEventRepository.findByProject_IdOrderByOccurredAtDesc(
                project.getId(),
                PageRequest.of(0, 1)
        ).getContent().get(0);
        long queryCountAfterLookup = statistics.getPrepareStatementCount();

        String projectName = found.getProject().getName();

        assertThat(projectName).isEqualTo("payment-service");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(queryCountAfterLookup + 1);
    }

    @Test
    void rejectsMissingErrorCode() {
        assertThatThrownBy(() -> ErrorEvent.create(
                new Project("payment-service"),
                null,
                "missing error code",
                OCCURRED_AT,
                RECEIVED_AT
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("errorCode must not be null");
    }

    @Test
    void rejectsMissingProject() {
        assertThatThrownBy(() -> ErrorEvent.create(
                null,
                ERROR_CODE,
                "missing project",
                OCCURRED_AT,
                RECEIVED_AT
        ))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("project must not be null");
    }

    @Test
    void rejectsMissingMessage() {
        assertThatThrownBy(() -> createErrorEvent(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("message must not be null");
    }

    @Test
    void rejectsBlankMessage() {
        assertThatThrownBy(() -> createErrorEvent("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("message must not be blank");
    }

    private ErrorEvent createErrorEvent(String message) {
        return ErrorEvent.create(
                saveProject("payment-service"),
                ERROR_CODE,
                message,
                OCCURRED_AT,
                RECEIVED_AT
        );
    }

    private Project saveProject(String name) {
        return projectRepository.saveAndFlush(new Project(name));
    }

    private Statistics statistics() {
        return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    }
}
