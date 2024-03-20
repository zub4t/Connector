/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *
 */

package org.eclipse.edc.connector.api.management.contractnegotiation;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.links.Link;
import io.swagger.v3.oas.annotations.links.LinkParameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import org.eclipse.edc.api.model.ApiCoreSchema;
import org.eclipse.edc.connector.api.management.configuration.ManagementApiSchema;
import org.eclipse.edc.connector.api.management.contractnegotiation.model.ContractOfferDescription;
import org.eclipse.edc.connector.api.management.contractnegotiation.model.NegotiationState;

import java.util.List;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;
import static org.eclipse.edc.connector.contract.spi.types.command.TerminateNegotiationCommand.TERMINATE_NEGOTIATION_TYPE;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.CONTRACT_REQUEST_TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;

@OpenAPIDefinition
@Tag(name = "Contract Negotiation")
public interface ContractNegotiationApi {

    @Operation(description = "Returns all contract negotiations according to a query",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = ApiCoreSchema.QuerySpecSchema.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The contract negotiations that match the query",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ManagementApiSchema.ContractNegotiationSchema.class)))),
                    @ApiResponse(responseCode = "400", description = "Request was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))) }
    )
    JsonArray queryNegotiations(JsonObject querySpecJson);

    @Operation(description = "Gets a contract negotiation with the given ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The contract negotiation",
                            content = @Content(schema = @Schema(implementation = ManagementApiSchema.ContractNegotiationSchema.class))),
                    @ApiResponse(responseCode = "400", description = "Request was malformed, e.g. id was null",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "An contract negotiation with the given ID does not exist",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            }
    )
    JsonObject getNegotiation(String id);

    @Operation(description = "Gets the state of a contract negotiation with the given ID",
            operationId = "getNegotiationState",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The contract negotiation's state",
                            content = @Content(schema = @Schema(implementation = NegotiationState.class))),
                    @ApiResponse(responseCode = "400", description = "Request was malformed, e.g. id was null",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "An contract negotiation with the given ID does not exist",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            }
    )
    JsonObject getNegotiationState(String id);

    @Operation(description = "Gets a contract agreement for a contract negotiation with the given ID",
            responses = {
                    @ApiResponse(responseCode = "200", description = "The contract agreement that is attached to the negotiation, or null",
                            content = @Content(schema = @Schema(implementation = ManagementApiSchema.ContractAgreementSchema.class))),
                    @ApiResponse(responseCode = "400", description = "Request was malformed, e.g. id was null",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "An contract negotiation with the given ID does not exist",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            }
    )
    JsonObject getAgreementForNegotiation(String negotiationId);

    @Operation(description = "Initiates a contract negotiation for a given offer and with the given counter part. Please note that successfully invoking this endpoint " +
            "only means that the negotiation was initiated. Clients must poll the /{id}/state endpoint to track the state",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = ContractRequestSchema.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "The negotiation was successfully initiated. Returns the contract negotiation ID and created timestamp",
                            content = @Content(schema = @Schema(implementation = ApiCoreSchema.IdResponseSchema.class)),
                            links = @Link(name = "poll-state", operationId = "getNegotiationState", parameters = {
                                    @LinkParameter(name = "id", expression = "$response.body#/id")
                            })
                    ),
                    @ApiResponse(responseCode = "400", description = "Request body was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
            })
    JsonObject initiateContractNegotiation(JsonObject requestDto);

    @Operation(description = "Terminates the contract negotiation.",
            requestBody = @RequestBody(content = @Content(schema = @Schema(implementation = TerminateNegotiationSchema.class))),
            responses = {
                    @ApiResponse(responseCode = "200", description = "ContractNegotiation is terminating",
                            links = @Link(name = "poll-state", operationId = "getNegotiationState")),
                    @ApiResponse(responseCode = "400", description = "Request was malformed",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class)))),
                    @ApiResponse(responseCode = "404", description = "A contract negotiation with the given ID does not exist",
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = ApiCoreSchema.ApiErrorDetailSchema.class))))
            }
    )
    void terminateNegotiation(String id, JsonObject terminateNegotiation);

    @Schema(name = "ContractRequest", example = ContractRequestSchema.CONTRACT_REQUEST_EXAMPLE)
    record ContractRequestSchema(
            @Schema(name = CONTEXT, requiredMode = REQUIRED)
            Object context,
            @Schema(name = TYPE, example = CONTRACT_REQUEST_TYPE)
            String type,
            @Schema(requiredMode = REQUIRED)
            String protocol,
            @Deprecated(since = "0.3.2")
            @Schema(deprecated = true, description = "please use counterPartyAddress instead")
            String connectorAddress,
            @Schema(requiredMode = REQUIRED)
            String counterPartyAddress,
            @Deprecated(since = "0.5.1")
            @Schema(deprecated = true, description = "please use policy.assigner instead")
            String providerId,
            @Deprecated(since = "0.3.2")
            @Schema(deprecated = true, description = "please use policy instead of offer")
            ContractOfferDescriptionSchema offer,
            ManagementApiSchema.PolicySchema policy,
            List<ManagementApiSchema.CallbackAddressSchema> callbackAddresses) {

        // policy example took from https://w3c.github.io/odrl/bp/
        public static final String CONTRACT_REQUEST_EXAMPLE = """
                {
                    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                    "@type": "https://w3id.org/edc/v0.0.1/ns/ContractRequest",
                    "counterPartyAddress": "http://provider-address",
                    "protocol": "dataspace-protocol-http",
                    "policy": {
                        "@context": "http://www.w3.org/ns/odrl.jsonld",
                        "@type": "odrl:Offer",
                        "@id": "policy-id",
                        "assigner": "providerId",
                        "permission": [],
                        "prohibition": [],
                        "obligation": [],
                        "target": "assetId"
                    },
                    "callbackAddresses": [{
                        "transactional": false,
                        "uri": "http://callback/url",
                        "events": ["contract.negotiation", "transfer.process"],
                        "authKey": "auth-key",
                        "authCodeId": "auth-code-id"
                    }]
                }
                """;
    }

    @Schema(name = "NegotiationState", example = NegotiationStateSchema.NEGOTIATION_STATE_EXAMPLE)
    record NegotiationStateSchema(
            @Schema(name = TYPE, example = NegotiationState.NEGOTIATION_STATE_TYPE)
            String ldType,
            String state
    ) {
        public static final String NEGOTIATION_STATE_EXAMPLE = """
                {
                    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                    "@type": "https://w3id.org/edc/v0.0.1/ns/NegotiationState",
                    "state": "REQUESTED"
                }
                """;
    }

    @Schema(name = "ContractOfferDescription")
    record ContractOfferDescriptionSchema(
            @Schema(name = TYPE, example = ContractOfferDescription.CONTRACT_OFFER_DESCRIPTION_TYPE)
            String type,
            String offerId,
            String assetId,
            ManagementApiSchema.PolicySchema policy
    ) {

    }

    @Schema(example = TerminateNegotiationSchema.TERMINATE_NEGOTIATION_EXAMPLE)
    record TerminateNegotiationSchema(
            @Schema(name = TYPE, example = TERMINATE_NEGOTIATION_TYPE)
            String ldType,
            @Schema(name = ID)
            String id,
            String reason
    ) {
        public static final String TERMINATE_NEGOTIATION_EXAMPLE = """
                {
                    "@context": { "@vocab": "https://w3id.org/edc/v0.0.1/ns/" },
                    "@type": "https://w3id.org/edc/v0.0.1/ns/TerminateNegotiation",
                    "@id": "negotiation-id",
                    "reason": "a reason to terminate"
                }
                """;
    }

}
