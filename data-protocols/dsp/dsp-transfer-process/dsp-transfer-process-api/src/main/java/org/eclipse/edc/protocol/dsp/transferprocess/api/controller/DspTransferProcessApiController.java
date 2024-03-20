/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.transferprocess.api.controller;

import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.spi.transferprocess.TransferProcessProtocolService;
import org.eclipse.edc.connector.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferCompletionMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferRequestMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferStartMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferSuspensionMessage;
import org.eclipse.edc.connector.transfer.spi.types.protocol.TransferTerminationMessage;
import org.eclipse.edc.protocol.dsp.spi.message.DspRequestHandler;
import org.eclipse.edc.protocol.dsp.spi.message.GetDspRequest;
import org.eclipse.edc.protocol.dsp.spi.message.PostDspRequest;

import static jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.BASE_PATH;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_COMPLETION;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_INITIAL_REQUEST;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_START;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_SUSPENSION;
import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.TRANSFER_TERMINATION;
import static org.eclipse.edc.protocol.dsp.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_COMPLETION_MESSAGE;
import static org.eclipse.edc.protocol.dsp.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_ERROR;
import static org.eclipse.edc.protocol.dsp.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE;
import static org.eclipse.edc.protocol.dsp.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_START_MESSAGE;
import static org.eclipse.edc.protocol.dsp.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_SUSPENSION_MESSAGE;
import static org.eclipse.edc.protocol.dsp.type.DspTransferProcessPropertyAndTypeNames.DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE;

/**
 * Provides the endpoints for receiving messages regarding transfers, like initiating, completing
 * and terminating a transfer process.
 */
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Path(BASE_PATH)
public class DspTransferProcessApiController {

    private final TransferProcessProtocolService protocolService;
    private final DspRequestHandler dspRequestHandler;

    public DspTransferProcessApiController(TransferProcessProtocolService protocolService, DspRequestHandler dspRequestHandler) {
        this.protocolService = protocolService;
        this.dspRequestHandler = dspRequestHandler;
    }

    /**
     * Retrieves an existing transfer process. This functionality is not yet supported.
     *
     * @param id the ID of the process
     * @return the requested transfer process or an error.
     */
    @GET
    @Path("/{id}")
    public Response getTransferProcess(@PathParam("id") String id, @HeaderParam(AUTHORIZATION) String token) {
        var request = GetDspRequest.Builder.newInstance(TransferProcess.class)
                .id(id).token(token).errorType(DSPACE_TYPE_TRANSFER_ERROR)
                .serviceCall(protocolService::findById)
                .build();

        return dspRequestHandler.getResource(request);
    }

    /**
     * Initiates a new transfer process that has been requested by the counter-party.
     *
     * @param jsonObject the {@link TransferRequestMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return the created transfer process or an error.
     */
    @POST
    @Path(TRANSFER_INITIAL_REQUEST)
    public Response initiateTransferProcess(JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        var request = PostDspRequest.Builder.newInstance(TransferRequestMessage.class, TransferProcess.class)
                .message(jsonObject)
                .token(token)
                .expectedMessageType(DSPACE_TYPE_TRANSFER_REQUEST_MESSAGE)
                .serviceCall(protocolService::notifyRequested)
                .errorType(DSPACE_TYPE_TRANSFER_ERROR)
                .build();

        return dspRequestHandler.createResource(request);
    }

    /**
     * Notifies the connector that a transfer process has been started by the counter-part.
     *
     * @param id         the ID of the process
     * @param jsonObject the {@link TransferStartMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return empty response or error.
     */
    @POST
    @Path("{id}" + TRANSFER_START)
    public Response transferProcessStart(@PathParam("id") String id, JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        var request = PostDspRequest.Builder.newInstance(TransferStartMessage.class, TransferProcess.class)
                .processId(id)
                .expectedMessageType(DSPACE_TYPE_TRANSFER_START_MESSAGE)
                .message(jsonObject)
                .token(token)
                .serviceCall(protocolService::notifyStarted)
                .errorType(DSPACE_TYPE_TRANSFER_ERROR)
                .build();

        return dspRequestHandler.updateResource(request);
    }

    /**
     * Notifies the connector that a transfer process has been completed by the counter-part.
     *
     * @param id         the ID of the process
     * @param jsonObject the {@link TransferCompletionMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return empty response or error.
     */
    @POST
    @Path("{id}" + TRANSFER_COMPLETION)
    public Response transferProcessCompletion(@PathParam("id") String id, JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        var request = PostDspRequest.Builder.newInstance(TransferCompletionMessage.class, TransferProcess.class)
                .processId(id)
                .expectedMessageType(DSPACE_TYPE_TRANSFER_COMPLETION_MESSAGE)
                .message(jsonObject)
                .token(token)
                .serviceCall(protocolService::notifyCompleted)
                .errorType(DSPACE_TYPE_TRANSFER_ERROR)
                .build();

        return dspRequestHandler.updateResource(request);
    }

    /**
     * Notifies the connector that a transfer process has been terminated by the counter-part.
     *
     * @param id         the ID of the process
     * @param jsonObject the {@link TransferTerminationMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return empty response or error.
     */
    @POST
    @Path("{id}" + TRANSFER_TERMINATION)
    public Response transferProcessTermination(@PathParam("id") String id, JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        var request = PostDspRequest.Builder.newInstance(TransferTerminationMessage.class, TransferProcess.class)
                .processId(id)
                .expectedMessageType(DSPACE_TYPE_TRANSFER_TERMINATION_MESSAGE)
                .message(jsonObject)
                .token(token)
                .serviceCall(protocolService::notifyTerminated)
                .errorType(DSPACE_TYPE_TRANSFER_ERROR)
                .build();

        return dspRequestHandler.updateResource(request);
    }

    /**
     * Notifies the connector that a transfer process has been suspended by the counter-part.
     *
     * @param id         the ID of the process
     * @param jsonObject the {@link TransferSuspensionMessage} in JSON-LD expanded form
     * @param token      the authorization header
     * @return empty response or error.
     */
    @POST
    @Path("{id}" + TRANSFER_SUSPENSION)
    public Response transferProcessSuspension(@PathParam("id") String id, JsonObject jsonObject, @HeaderParam(AUTHORIZATION) String token) {
        var request = PostDspRequest.Builder.newInstance(TransferSuspensionMessage.class, TransferProcess.class)
                .processId(id)
                .expectedMessageType(DSPACE_TYPE_TRANSFER_SUSPENSION_MESSAGE)
                .message(jsonObject)
                .token(token)
                .serviceCall(protocolService::notifySuspended)
                .errorType(DSPACE_TYPE_TRANSFER_ERROR)
                .build();

        return dspRequestHandler.updateResource(request);
    }

}
