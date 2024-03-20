/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.connector.api.client.transferprocess;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.io.IOException;
import java.net.URI;

import static okhttp3.Protocol.HTTP_1_1;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.junit.testfixtures.TestUtils.testHttpClient;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

public class TransferProcessHttpClientTest {

    private final Interceptor interceptor = mock(Interceptor.class);
    private final Monitor monitor = mock();
    private TransferProcessHttpClient transferProcessHttpClient;

    private static Response createResponse(int code, InvocationOnMock invocation) {
        Interceptor.Chain chain = invocation.getArgument(0);
        return new Response.Builder()
                .request(chain.request())
                .protocol(HTTP_1_1).code(code)
                .body(ResponseBody.create("", MediaType.get("application/json"))).message("test")
                .build();
    }

    @BeforeEach
    void setup() {
        transferProcessHttpClient = new TransferProcessHttpClient(testHttpClient(interceptor), new ObjectMapper(), monitor);
    }

    @Test
    void complete() throws IOException {
        var req = createRequest().callbackAddress(URI.create("http://localhost:8080/test")).build();
        when(interceptor.intercept(any()))
                .thenAnswer(invocation -> createResponse(204, invocation));

        var result = transferProcessHttpClient.completed(req);

        assertThat(result).isSucceeded();

        verifyNoInteractions(monitor);
    }

    @Test
    void complete_shouldSucceed_withNoCallbacks() throws IOException {
        var req = createRequest().build();

        var result = transferProcessHttpClient.completed(req);

        assertThat(result).isSucceeded();

        verify(monitor).warning(anyString());
    }

    @Test
    void complete_shouldSucceed_withRetry() throws IOException {
        var req = createRequest().callbackAddress(URI.create("http://localhost:8080/test")).build();
        when(interceptor.intercept(any()))
                .thenAnswer(invocation -> createResponse(400, invocation))
                .thenAnswer(invocation -> createResponse(204, invocation));

        var result = transferProcessHttpClient.completed(req);

        assertThat(result).isSucceeded();

        verifyNoInteractions(monitor);
    }

    @Test
    void complete_shouldFail_withMaxRetryExceeded() throws IOException {
        var req = createRequest().callbackAddress(URI.create("http://localhost:8080/test")).build();
        when(interceptor.intercept(any()))
                .thenAnswer(invocation -> createResponse(400, invocation))
                .thenAnswer(invocation -> createResponse(400, invocation))
                .thenAnswer(invocation -> createResponse(400, invocation));

        var result = transferProcessHttpClient.completed(req);

        assertThat(result).isFailed();

        verify(monitor).severe(anyString(), any());
    }

    @Test
    void fail() throws IOException {
        var req = createRequest().callbackAddress(URI.create("http://localhost:8080/test")).build();
        when(interceptor.intercept(any()))
                .thenAnswer(invocation -> createResponse(204, invocation));

        var result = transferProcessHttpClient.failed(req, "failure");

        assertThat(result).isSucceeded();

        verifyNoInteractions(monitor);
    }

    private DataFlowStartMessage.Builder createRequest() {
        return DataFlowStartMessage.Builder.newInstance()
                .id("1")
                .processId("1")
                .sourceDataAddress(DataAddress.Builder.newInstance().type("type").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("type").build());
    }
}
