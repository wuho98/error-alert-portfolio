package com.wuho.erroralert;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mock.env.MockEnvironment;

class DevConfigurationBindingTest {

    @Test
    void devProfileBindsLocalInfrastructureDefaults() throws Exception {
        MockEnvironment environment = loadYaml("src/main/resources/application-dev.yml");

        DataSourceProperties datasource = Binder.get(environment)
                .bind("spring.datasource", DataSourceProperties.class)
                .orElseThrow(() -> new IllegalStateException("spring.datasource binding failed"));
        RedisProperties redis = Binder.get(environment)
                .bind("spring.data.redis", RedisProperties.class)
                .orElseThrow(() -> new IllegalStateException("spring.data.redis binding failed"));

        assertThat(datasource.getUrl())
                .isEqualTo("jdbc:mysql://localhost:13306/error_alert?serverTimezone=Asia/Seoul&characterEncoding=UTF-8");
        assertThat(datasource.getUsername()).isEqualTo("error_alert");
        assertThat(datasource.getPassword()).startsWith("replace_with_");
        assertThat(redis.getHost()).isEqualTo("localhost");
        assertThat(redis.getPort()).isEqualTo(16379);
    }

    private MockEnvironment loadYaml(String relativePath) throws IOException {
        Path projectRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        FileSystemResource resource = new FileSystemResource(projectRoot.resolve(relativePath));
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader().load(relativePath, resource);

        MockEnvironment environment = new MockEnvironment();
        for (PropertySource<?> propertySource : propertySources) {
            environment.getPropertySources().addLast(propertySource);
        }
        return environment;
    }
}
