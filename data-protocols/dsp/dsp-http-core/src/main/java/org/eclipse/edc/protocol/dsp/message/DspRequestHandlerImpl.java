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

package org.eclipse.edc.protocol.dsp.message;

import jakarta.json.JsonObject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.protocol.dsp.spi.message.DspRequestHandler;
import org.eclipse.edc.protocol.dsp.spi.message.GetDspRequest;
import org.eclipse.edc.protocol.dsp.spi.message.PostDspRequest;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.message.ProcessRemoteMessage;
import org.eclipse.edc.spi.types.domain.message.RemoteMessage;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;

import java.util.UUID;

import static org.eclipse.edc.protocol.dsp.spi.error.DspErrorResponse.type;
import static org.eclipse.edc.protocol.dsp.spi.types.HttpMessageProtocol.DATASPACE_PROTOCOL_HTTP;

public class DspRequestHandlerImpl implements DspRequestHandler {

    private final Monitor monitor;
    private final JsonObjectValidatorRegistry validatorRegistry;
    private final TypeTransformerRegistry transformerRegistry;

    public DspRequestHandlerImpl(Monitor monitor, JsonObjectValidatorRegistry validatorRegistry, TypeTransformerRegistry transformerRegistry) {
        this.monitor = monitor;
        this.validatorRegistry = validatorRegistry;
        this.transformerRegistry = transformerRegistry;
    }

    @Override
    public <R> Response getResource(GetDspRequest<R> request) {
        monitor.debug(() -> "DSP: Incoming resource request for %s id %s".formatted(request.getResultClass(), request.getId()));

        var token = request.getToken();
        if (token == null) {
            return type(request.getErrorType()).processId(request.getId()).unauthorized();
        }
        var tokenRepresentation = TokenRepresentation.Builder.newInstance().token(token).build();

        var serviceResult = request.getServiceCall().apply(request.getId(), tokenRepresentation);
        if (serviceResult.failed()) {
            monitor.debug(() -> "DSP: Service call failed: %s".formatted(serviceResult.getFailureDetail()));
            return type(request.getErrorType()).processId(request.getId()).from(serviceResult.getFailure());
        }

        var resource = serviceResult.getContent();

        var transformation = transformerRegistry.transform(resource, JsonObject.class);
        if (transformation.failed()) {
            var errorCode = UUID.randomUUID();
            monitor.warning("Error transforming %s, error id %s: %s".formatted(request.getResultClass().getSimpleName(), errorCode, transformation.getFailureDetail()));
            return type(request.getErrorType()).processId(request.getId()).message(String.format("Error code %s", errorCode)).internalServerError();
        }

        return Response.ok().type(MediaType.APPLICATION_JSON).entity(transformation.getContent()).build();
    }

    @Override
    public <I extends RemoteMessage, R> Response createResource(PostDspRequest<I, R> request) {
        monitor.debug(() -> "DSP: Incoming %s for %s process%s".formatted(
                request.getInputClass().getSimpleName(),
                request.getResultClass(),
                request.getProcessId() != null ? ": " + request.getProcessId() : ""));

        var token = request.getToken();
        if (token == null) {
            return type(request.getErrorType()).unauthorized();
        }

        var validation = validatorRegistry.validate(request.getExpectedMessageType(), request.getMessage());
        if (validation.failed()) {
            monitor.debug(() -> "DSP: Validation failed: %s".formatted(validation.getFailureMessages()));
            return type(request.getErrorType()).badRequest();
        }

        var inputTransformation = transformerRegistry.transform(request.getMessage(), request.getInputClass())
                .compose(message -> {
                    if (message instanceof ProcessRemoteMessage processRemoteMessage) {
                        processRemoteMessage.setProtocol(DATASPACE_PROTOCOL_HTTP);
                    }
                    return Result.success(message);
                });

        if (inputTransformation.failed()) {
            monitor.debug(() -> "DSP: Transformation failed: %s".formatted(inputTransformation.getFailureMessages()));
            return type(request.getErrorType()).badRequest();
        }

        var tokenRepresentation = TokenRepresentation.Builder.newInstance().token(token).build();

        var serviceResult = request.getServiceCall().apply(inputTransformation.getContent(), tokenRepresentation);
        if (serviceResult.failed()) {
            monitor.debug(() -> "DSP: Service call failed: %s".formatted(serviceResult.getFailureDetail()));
            return type(request.getErrorType()).from(serviceResult.getFailure());
        }

        var resource = serviceResult.getContent();

        var outputTransformation = transformerRegistry.transform(resource, JsonObject.class);
        if (outputTransformation.failed()) {
            var errorCode = UUID.randomUUID();
            monitor.warning("Error transforming %s, error id %s: %s".formatted(request.getResultClass().getSimpleName(), errorCode, outputTransformation.getFailureDetail()));
            return type(request.getErrorType()).message("Error code %s".formatted(errorCode)).internalServerError();
        }

        return Response.ok().type(MediaType.APPLICATION_JSON).entity(outputTransformation.getContent()).build();
    }

    @Override
    public <I extends RemoteMessage, R> Response updateResource(PostDspRequest<I, R> request) {
        monitor.debug(() -> "DSP: Incoming %s for %s process%s".formatted(
                request.getInputClass().getSimpleName(),
                request.getResultClass(),
                request.getProcessId() != null ? ": " + request.getProcessId() : ""));

        var token = request.getToken();
        if (token == null) {
            return type(request.getErrorType()).processId(request.getProcessId()).unauthorized();
        }

        var tokenRepresentation = TokenRepresentation.Builder.newInstance().token(request.getToken()).build();

        var validation = validatorRegistry.validate(request.getExpectedMessageType(), request.getMessage());
        if (validation.failed()) {
            monitor.debug(() -> "DSP: Validation failed: %s".formatted(validation.getFailureMessages()));
            return type(request.getErrorType()).processId(request.getProcessId()).badRequest();
        }

        var inputTransformation = transformerRegistry.transform(request.getMessage(), request.getInputClass())
                .compose(message -> {
                    if (message instanceof ProcessRemoteMessage processRemoteMessage) {
                        var processIdValidation = processRemoteMessage.isValidProcessId(request.getProcessId());
                        if (processIdValidation.succeeded()) {
                            processRemoteMessage.setProcessId(request.getProcessId());
                            processRemoteMessage.setProtocol(DATASPACE_PROTOCOL_HTTP);
                            return Result.success(message);
                        } else {
                            return Result.failure("DSP: %s".formatted(processIdValidation.getFailureDetail()));
                        }
                    } else {
                        return Result.success(message);
                    }
                });

        if (inputTransformation.failed()) {
            monitor.debug(() -> "DSP: Transformation failed: %s".formatted(validation.getFailureMessages()));
            return type(request.getErrorType()).processId(request.getProcessId()).badRequest();
        }

        return request.getServiceCall()
                .apply(inputTransformation.getContent(), tokenRepresentation)
                .map(it -> Response.ok().type(MediaType.APPLICATION_JSON_TYPE).build())
                .orElse(failure -> {
                    monitor.debug(() -> "DSP: Service call failed: %s".formatted(failure.getFailureDetail()));
                    return type(request.getErrorType()).processId(request.getProcessId()).from(failure);
                });
    }

}
