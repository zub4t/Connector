/*
 *  Copyright (c) 2022 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

package org.eclipse.edc.connector.dataplane.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.dataplane.selector.spi.client.DataPlaneClient;
import org.eclipse.edc.connector.dataplane.selector.spi.instance.DataPlaneInstance;
import org.eclipse.edc.connector.dataplane.spi.response.TransferErrorResponse;
import org.eclipse.edc.spi.response.ResponseStatus;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;

import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.junit.testfixtures.TestUtils.getFreePort;
import static org.eclipse.edc.junit.testfixtures.TestUtils.testHttpClient;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.matchers.Times.once;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpStatusCode.CONFLICT_409;
import static org.mockserver.model.HttpStatusCode.NO_CONTENT_204;
import static org.mockserver.stop.Stop.stopQuietly;

class RemoteDataPlaneClientTest {

    private static final ObjectMapper MAPPER = new TypeManager().getMapper();

    private static final int DATA_PLANE_API_PORT = getFreePort();
    private static final String DATA_PLANE_PATH = "/transfer";
    private static final String DATA_PLANE_API_URI = "http://localhost:" + DATA_PLANE_API_PORT + DATA_PLANE_PATH;
    private static ClientAndServer dataPlane;
    private final DataPlaneInstance instance = DataPlaneInstance.Builder.newInstance().url(DATA_PLANE_API_URI).build();
    private final DataPlaneClient dataPlaneClient = new RemoteDataPlaneClient(testHttpClient(), MAPPER, instance);

    @BeforeAll
    public static void setUp() {
        dataPlane = startClientAndServer(DATA_PLANE_API_PORT);
    }

    @AfterAll
    public static void tearDown() {
        stopQuietly(dataPlane);
    }

    private static HttpResponse withResponse(String errorMsg) throws JsonProcessingException {
        return response().withStatusCode(HttpStatusCode.BAD_REQUEST_400.code())
                .withBody(MAPPER.writeValueAsString(new TransferErrorResponse(List.of(errorMsg))), MediaType.APPLICATION_JSON);
    }

    private static DataFlowStartMessage createDataFlowRequest() {
        return DataFlowStartMessage.Builder.newInstance()
                .id("123")
                .processId("456")
                .sourceDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .destinationDataAddress(DataAddress.Builder.newInstance().type("test").build())
                .build();
    }

    @AfterEach
    public void resetMockServer() {
        dataPlane.reset();
    }

    @Test
    void transfer_verifyReturnFatalErrorIfReceiveResponseWithNullBody() throws JsonProcessingException {
        var flowRequest = createDataFlowRequest();

        var httpRequest = new HttpRequest().withPath(DATA_PLANE_PATH).withBody(MAPPER.writeValueAsString(flowRequest));
        dataPlane.when(httpRequest, once()).respond(response().withStatusCode(HttpStatusCode.BAD_REQUEST_400.code()));

        var result = dataPlaneClient.start(flowRequest);

        dataPlane.verify(httpRequest, VerificationTimes.once());

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailure().status()).isEqualTo(ResponseStatus.FATAL_ERROR);
        assertThat(result.getFailureMessages())
                .anySatisfy(s -> assertThat(s)
                        .isEqualTo("Transfer request failed with status code 400 for request %s: failed to read response body", flowRequest.getId())
                );
    }

    @Test
    void transfer_verifyReturnFatalErrorIfReceiveErrorInResponse() throws JsonProcessingException {
        var flowRequest = createDataFlowRequest();

        var httpRequest = new HttpRequest().withPath(DATA_PLANE_PATH).withBody(MAPPER.writeValueAsString(flowRequest));
        var errorMsg = UUID.randomUUID().toString();
        dataPlane.when(httpRequest, once()).respond(withResponse(errorMsg));

        var result = dataPlaneClient.start(flowRequest);

        dataPlane.verify(httpRequest, VerificationTimes.once());

        assertThat(result.failed()).isTrue();
        assertThat(result.getFailure().status()).isEqualTo(ResponseStatus.FATAL_ERROR);
        assertThat(result.getFailureMessages())
                .anySatisfy(s -> assertThat(s)
                        .isEqualTo(format("Transfer request failed with status code 400 for request %s: %s", flowRequest.getId(), errorMsg))
                );
    }

    @Test
    void transfer_verifyTransferSuccess() throws JsonProcessingException {
        var flowRequest = createDataFlowRequest();

        var httpRequest = new HttpRequest().withPath(DATA_PLANE_PATH).withBody(MAPPER.writeValueAsString(flowRequest));
        dataPlane.when(httpRequest, once()).respond(response().withStatusCode(HttpStatusCode.OK_200.code()));

        var result = dataPlaneClient.start(flowRequest);

        dataPlane.verify(httpRequest, VerificationTimes.once());

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void terminate_shouldCallTerminateOnAllTheAvailableDataPlanes() {
        var httpRequest = new HttpRequest().withMethod("DELETE").withPath(DATA_PLANE_PATH + "/processId");
        dataPlane.when(httpRequest, once()).respond(response().withStatusCode(NO_CONTENT_204.code()));

        var result = dataPlaneClient.terminate("processId");

        assertThat(result).isSucceeded();
        dataPlane.verify(httpRequest, VerificationTimes.once());
    }

    @Test
    void terminate_shouldFail_whenConflictResponse() {
        var httpRequest = new HttpRequest().withMethod("DELETE").withPath(DATA_PLANE_PATH + "/processId");
        dataPlane.when(httpRequest, once()).respond(response().withStatusCode(CONFLICT_409.code()));

        var result = dataPlaneClient.terminate("processId");

        assertThat(result).isFailed();
    }
}
