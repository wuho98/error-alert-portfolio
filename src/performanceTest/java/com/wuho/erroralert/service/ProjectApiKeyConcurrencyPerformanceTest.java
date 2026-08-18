package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wuho.erroralert.api.common.ApiErrorCode;
import com.wuho.erroralert.api.common.ApiException;
import com.wuho.erroralert.api.dto.ProjectApiKeyCreateResponse;
import com.wuho.erroralert.domain.Project;
import com.wuho.erroralert.repository.ProjectApiKeyRepository;
import com.wuho.erroralert.repository.ProjectRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * API key concurrency 증거 테스트: 실제 MySQL에서 동일 프로젝트로 API key 발급이 동시에 들어와도
 * 활성 key가 정확히 1개만 생성되는지 검증한다.
 *
 * H2({@code test} 프로필, {@code ddl-auto: create-drop})는 Flyway 마이그레이션을 타지 않아
 * {@code uk_project_api_key_active_project} 제약이 존재하지 않으므로, 이 테스트는 Testcontainers로
 * 띄운 실제 MySQL 8.4에 Flyway 마이그레이션을 그대로 적용한 뒤 검증한다.
 */
@Testcontainers
@SpringBootTest
class ProjectApiKeyConcurrencyPerformanceTest {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("error_alert")
            .withUsername("error_alert")
            .withPassword("test_mysql_password")
            .withStartupTimeout(Duration.ofMinutes(3));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.docker.compose.enabled", () -> "false");
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired
    private ProjectApiKeyService projectApiKeyService;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectApiKeyRepository projectApiKeyRepository;

    @Test
    void onlyOneConcurrentIssueRequestSucceedsPerProject() throws Exception {
        Project project = projectRepository.saveAndFlush(new Project("api-key-concurrency-concurrency-test-project"));

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Callable<Result>> tasks = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            tasks.add(() -> {
                startGate.await();
                try {
                    ProjectApiKeyCreateResponse response = projectApiKeyService.create(project.getId());
                    return Result.success(response);
                } catch (ApiException exception) {
                    return Result.failure(exception.getErrorCode());
                }
            });
        }

        List<Future<Result>> futures = new ArrayList<>();
        for (Callable<Result> task : tasks) {
            futures.add(executor.submit(task));
        }
        startGate.countDown();

        List<Result> results = new ArrayList<>();
        for (Future<Result> future : futures) {
            results.add(future.get(30, TimeUnit.SECONDS));
        }
        executor.shutdown();

        long successCount = results.stream().filter(Result::isSuccess).count();
        long conflictCount = results.stream()
                .filter(result -> !result.isSuccess())
                .filter(result -> result.errorCode() == ApiErrorCode.PROJECT_CONFLICT)
                .count();

        assertThat(successCount).isEqualTo(1);
        assertThat(conflictCount).isEqualTo(CONCURRENT_REQUESTS - 1);

        long activeKeyRowCount = projectApiKeyRepository.findAll().stream()
                .filter(key -> key.getProject().getId().equals(project.getId()))
                .filter(key -> key.getRevokedAt() == null)
                .count();
        assertThat(activeKeyRowCount).isEqualTo(1);
    }

    private record Result(boolean isSuccess, ApiErrorCode errorCode) {

        static Result success(ProjectApiKeyCreateResponse response) {
            return new Result(true, null);
        }

        static Result failure(ApiErrorCode errorCode) {
            return new Result(false, errorCode);
        }
    }
}
