package com.wuho.erroralert.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyGeneratorTest {

    private final ApiKeyGenerator apiKeyGenerator = new ApiKeyGenerator();

    @Test
    void generatesKeyWithPrefixAndSufficientLength() {
        String rawKey = apiKeyGenerator.generateRawKey();

        assertThat(rawKey).startsWith("pk_live_");
        assertThat(rawKey.length()).isGreaterThan(30);
    }

    @Test
    void generatesDifferentKeysEachTime() {
        String first = apiKeyGenerator.generateRawKey();
        String second = apiKeyGenerator.generateRawKey();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashIsDeterministicAndDoesNotContainRawKey() {
        String rawKey = apiKeyGenerator.generateRawKey();

        String hash1 = apiKeyGenerator.hash(rawKey);
        String hash2 = apiKeyGenerator.hash(rawKey);

        assertThat(hash1).isEqualTo(hash2).hasSize(64).doesNotContain(rawKey);
    }

    @Test
    void differentKeysProduceDifferentHashes() {
        String hash1 = apiKeyGenerator.hash(apiKeyGenerator.generateRawKey());
        String hash2 = apiKeyGenerator.hash(apiKeyGenerator.generateRawKey());

        assertThat(hash1).isNotEqualTo(hash2);
    }
}
