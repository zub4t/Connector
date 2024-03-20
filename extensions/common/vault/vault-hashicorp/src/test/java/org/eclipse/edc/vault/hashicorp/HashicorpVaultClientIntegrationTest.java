/*
 *  Copyright (c) 2024 Mercedes-Benz Tech Innovation GmbH
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Mercedes-Benz Tech Innovation GmbH - Implement automatic Hashicorp Vault token renewal
 *
 */

package org.eclipse.edc.vault.hashicorp;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import org.eclipse.edc.junit.annotations.ComponentTest;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.spi.monitor.ConsoleMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.vault.VaultContainer;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

@ComponentTest
@Testcontainers
class HashicorpVaultClientIntegrationTest {
    @Container
    static final VaultContainer<?> VAULT_CONTAINER = new VaultContainer<>("vault:1.9.6")
            .withVaultToken(UUID.randomUUID().toString());

    private static final String HTTP_URL_FORMAT = "http://%s:%s";
    private static final String HEALTH_CHECK_PATH = "/health/path";
    private static final String CLIENT_TOKEN_KEY = "client_token";
    private static final String AUTH_KEY = "auth";
    private static final long CREATION_TTL = 6L;
    private static final long TTL = 5L;
    private static final long RENEW_BUFFER = 4L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ConsoleMonitor MONITOR = new ConsoleMonitor();

    private static HashicorpVaultClient client;

    @BeforeEach
    void beforeEach() throws IOException, InterruptedException {
        assertThat(CREATION_TTL).isGreaterThan(TTL);
        client = new HashicorpVaultClient(
                TestUtils.testHttpClient(),
                OBJECT_MAPPER,
                MONITOR,
                getSettings()
        );
    }

    @Test
    void lookUpToken_whenTokenNotExpired_shouldSucceed() {
        var tokenLookUpResult = client.isTokenRenewable();

        assertThat(tokenLookUpResult).isSucceeded().isEqualTo(true);
    }

    @Test
    void lookUpToken_whenTokenExpired_shouldFail() {
        await()
                .pollDelay(CREATION_TTL, TimeUnit.SECONDS)
                .atMost(CREATION_TTL + 1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var tokenLookUpResult = client.isTokenRenewable();
                    assertThat(tokenLookUpResult).isFailed();
                    assertThat(tokenLookUpResult.getFailureDetail()).isEqualTo("Token look up failed with status 403");
                });
    }

    @Test
    void renewToken_whenTokenNotExpired_shouldSucceed() {
        var tokenRenewResult = client.renewToken();

        assertThat(tokenRenewResult).isSucceeded().satisfies(ttl -> assertThat(ttl).isEqualTo(TTL));
    }

    @Test
    void renewToken_whenTokenExpired_shouldFail() {
        await()
                .pollDelay(CREATION_TTL, TimeUnit.SECONDS)
                .atMost(CREATION_TTL + 1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    var tokenRenewResult = client.renewToken();
                    assertThat(tokenRenewResult).isFailed();
                    assertThat(tokenRenewResult.getFailureDetail()).isEqualTo("Token renew failed with status: 403");
                });
    }

    public static HashicorpVaultSettings getSettings() throws IOException, InterruptedException {
        var execResult = VAULT_CONTAINER.execInContainer(
                "vault",
                "token",
                "create",
                "-policy=root",
                "-ttl=%d".formatted(CREATION_TTL),
                "-format=json");

        var jsonParser = Json.createParser(new StringReader(execResult.getStdout()));
        jsonParser.next();
        var auth = jsonParser.getObjectStream().filter(e -> e.getKey().equals(AUTH_KEY))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow()
                .asJsonObject();
        var clientToken = auth.getString(CLIENT_TOKEN_KEY);

        return HashicorpVaultSettings.Builder.newInstance()
                .url(HTTP_URL_FORMAT.formatted(VAULT_CONTAINER.getHost(), VAULT_CONTAINER.getFirstMappedPort()))
                .healthCheckPath(HEALTH_CHECK_PATH)
                .token(clientToken)
                .ttl(TTL)
                .renewBuffer(RENEW_BUFFER)
                .build();
    }
}
