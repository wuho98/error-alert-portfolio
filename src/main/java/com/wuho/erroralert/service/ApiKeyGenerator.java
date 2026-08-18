package com.wuho.erroralert.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * API key 원문 생성과 저장용 해시 변환을 담당한다. 원문은 절대 저장하지 않는다.
 */
@Component
public class ApiKeyGenerator {

    private static final String PREFIX = "pk_live_";
    private static final int RANDOM_BYTE_LENGTH = 32;
    private static final String HASH_ALGORITHM = "SHA-256";

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateRawKey() {
        byte[] randomBytes = new byte[RANDOM_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(HASH_ALGORITHM + " 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
