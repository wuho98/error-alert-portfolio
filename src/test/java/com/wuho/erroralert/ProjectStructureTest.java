package com.wuho.erroralert;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectStructureTest {

    private static final List<String> LAYER_PACKAGES = List.of(
            "api",
            "service",
            "domain",
            "repository",
            "config");

    @Test
    void mainApplicationClassStaysInRootPackage() {
        assertThat(ErrorAlertApplication.class.getPackageName())
                .isEqualTo("com.wuho.erroralert");
    }

    @Test
    void layerPackagesHaveTrackedPackageDescriptors() {
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path packageRoot = projectRoot.resolve(
                Path.of("src", "main", "java", "com", "wuho", "erroralert"));

        for (String layerPackage : LAYER_PACKAGES) {
            assertThat(Files.exists(packageRoot.resolve(layerPackage).resolve("package-info.java")))
                    .as("%s package descriptor exists", layerPackage)
                    .isTrue();
        }
    }

    @Test
    void devEnvironmentConfigurationIsDocumentedAndSecretsStayIgnored() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

        // 공통 설정은 기본 profile만 갖고, 실제 연결값은 profile별 설정으로 분리한다.
        assertThat(Files.readString(projectRoot.resolve("src/main/resources/application.yml")))
                .contains("default: dev");

        // dev profile은 .env import와 MYSQL/REDIS 변수 체계를 사용한다.
        assertThat(Files.readString(projectRoot.resolve("src/main/resources/application-dev.yml")))
                .contains("optional:file:.env[.properties]")
                .contains("on-profile: dev")
                .contains("docker:")
                .contains("compose:")
                .contains("enabled: ${DOCKER_COMPOSE_ENABLED:true}")
                .contains("file: compose.yml")
                .contains("lifecycle-management: start-only")
                .contains("MYSQL_HOST:localhost")
                .contains("MYSQL_PORT:13306")
                .contains("MYSQL_DATABASE:error_alert")
                .contains("MYSQL_USER:error_alert")
                .contains("REDIS_HOST")
                .contains("REDIS_PORT:16379");

        // 테스트 기본 profile은 test이고, 테스트 리소스에서 H2 기준을 관리한다.
        assertThat(Files.readString(projectRoot.resolve("src/test/resources/application.properties")))
                .contains("spring.profiles.default=test");

        assertThat(Files.readString(projectRoot.resolve("src/test/resources/application-test.yml")))
                .contains("on-profile: test")
                .contains("jdbc:h2:mem:error_alert")
                .contains("ddl-auto: create-drop")
                .contains("flyway:")
                .contains("enabled: false");

        // .env.example은 공유 가능한 예시만 담고 실제 개인 값은 .env에 둔다.
        assertThat(Files.readString(projectRoot.resolve(".env.example")))
                .contains("MYSQL_HOST=localhost")
                .contains("MYSQL_PORT=13306")
                .contains("REDIS_PORT=16379")
                .contains("DOCKER_COMPOSE_ENABLED=true")
                .contains("replace_with_dev_mysql_password")
                .contains("Do not put personal passwords");

        assertThat(Files.readString(projectRoot.resolve("README.md")))
                .contains("bootRun")
                .contains("DOCKER_COMPOSE_ENABLED=false")
                .contains(".\\gradlew.bat bootRun")
                .contains("./gradlew bootRun")
                .contains("MYSQL_*")
                .contains("REDIS_*");

        // Compose 기본 포트와 placeholder는 .env.example 기준과 맞아야 한다.
        assertThat(Files.readString(projectRoot.resolve("compose.yml")))
                .contains("MYSQL_PORT:-13306")
                .contains("REDIS_PORT:-16379")
                .contains("org.springframework.boot.jdbc.parameters")
                .contains("replace_with_dev_mysql_password");

        assertThat(Files.readString(projectRoot.resolve("build.gradle")))
                .contains("developmentOnly 'org.springframework.boot:spring-boot-docker-compose'");

        // 로컬 비밀값 파일은 Git 추적 대상에서 제외한다.
        assertThat(Files.readString(projectRoot.resolve(".gitignore")))
                .contains(".env")
                .contains(".env.*")
                .contains("!.env.example");
    }

    @Test
    void devEnvironmentConfigurationRejectsOldLocalDefaultsAndCommittedSecrets() throws Exception {
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        String application = Files.readString(projectRoot.resolve("src/main/resources/application.yml"));
        String devApplication = Files.readString(projectRoot.resolve("src/main/resources/application-dev.yml"));
        String testApplication = Files.readString(projectRoot.resolve("src/test/resources/application-test.yml"));
        String envExample = Files.readString(projectRoot.resolve(".env.example"));
        String compose = Files.readString(projectRoot.resolve("compose.yml"));

        assertThat(application)
                .doesNotContain("default: local")
                .doesNotContain("datasource:")
                .doesNotContain("DB_URL")
                .doesNotContain("DB_USERNAME")
                .doesNotContain("DB_PASSWORD");

        assertThat(devApplication)
                .doesNotContain("on-profile: local")
                .doesNotContain("DB_URL")
                .doesNotContain("DB_USERNAME")
                .doesNotContain("DB_PASSWORD")
                .doesNotContain("MYSQL_PORT:3306")
                .doesNotContain("REDIS_PORT:6379")
                .doesNotContain("local_password")
                .doesNotContain("local_root_password");

        assertThat(testApplication)
                .doesNotContain("on-profile: local")
                .doesNotContain("jdbc:mysql:")
                .doesNotContain("MYSQL_PORT")
                .doesNotContain("REDIS_PORT");

        assertThat(envExample)
                .doesNotContain("DB_URL")
                .doesNotContain("DB_USERNAME")
                .doesNotContain("DB_PASSWORD")
                .doesNotContain("MYSQL_PORT=3306")
                .doesNotContain("REDIS_PORT=6379")
                .doesNotContain("local_password")
                .doesNotContain("local_root_password")
                .doesNotContain("gmail.com")
                .doesNotContainPattern("[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}");

        assertThat(compose)
                .doesNotContain("MYSQL_PORT:-3306")
                .doesNotContain("REDIS_PORT:-6379")
                .doesNotContain("local_password")
                .doesNotContain("local_root_password");
    }
}
