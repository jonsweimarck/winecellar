package com.example.winecellar.application;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void skaGenerera256BitarsSlumpmässigaOchUnikaTokens() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = TokenHasher.generate();
            // 32 byte base64url utan padding = 43 tecken
            assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(1000);
    }

    @Test
    void skaHashaDeterministisktOchInteLämnaUtDetRådaTokenet() {
        String token = TokenHasher.generate();
        String hash = TokenHasher.hash(token);
        assertThat(hash).isEqualTo(TokenHasher.hash(token)).hasSize(64).doesNotContain(token);
        assertThat(TokenHasher.hash(token + "x")).isNotEqualTo(hash);
    }

    @Test
    void skaJämföraRåttTokenMotLagradHash() {
        String token = TokenHasher.generate();
        String hash = TokenHasher.hash(token);
        assertThat(TokenHasher.matches(token, hash)).isTrue();
        assertThat(TokenHasher.matches(TokenHasher.generate(), hash)).isFalse();
        assertThat(TokenHasher.matches(null, hash)).isFalse();
        assertThat(TokenHasher.matches(token, null)).isFalse();
        // Det lagrade värdet är hashen, inte tokenet - tokenet självt matchar inte som "hash".
        assertThat(TokenHasher.matches(token, token)).isFalse();
    }
}
