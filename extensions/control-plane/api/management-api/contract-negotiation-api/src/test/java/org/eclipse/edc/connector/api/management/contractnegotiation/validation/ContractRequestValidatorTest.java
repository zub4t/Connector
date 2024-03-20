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

package org.eclipse.edc.connector.api.management.contractnegotiation.validation;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.validator.spi.ValidationFailure;
import org.eclipse.edc.validator.spi.Validator;
import org.eclipse.edc.validator.spi.Violation;
import org.junit.jupiter.api.Test;

import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.eclipse.edc.connector.api.management.contractnegotiation.model.ContractOfferDescription.ASSET_ID;
import static org.eclipse.edc.connector.api.management.contractnegotiation.model.ContractOfferDescription.OFFER_ID;
import static org.eclipse.edc.connector.api.management.contractnegotiation.model.ContractOfferDescription.POLICY;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.CONNECTOR_ADDRESS;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.CONTRACT_REQUEST_TYPE;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.OFFER;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.PROTOCOL;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.PROVIDER_ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_ASSIGNER_ATTRIBUTE;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_POLICY_TYPE_OFFER;
import static org.eclipse.edc.jsonld.spi.PropertyAndTypeNames.ODRL_TARGET_ATTRIBUTE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ContractRequestValidatorTest {

    private final Monitor monitor = mock();
    private final Validator<JsonObject> validator = ContractRequestValidator.instance(monitor);

    @Test
    void shouldSuccess_whenObjectIsValid() {
        var input = Json.createObjectBuilder()
                .add(CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS, value("http://connector-address"))
                .add(PROTOCOL, value("protocol"))
                .add(POLICY, createArrayBuilder().add(createObjectBuilder()
                        .add(TYPE, createArrayBuilder().add(ODRL_POLICY_TYPE_OFFER))
                        .add(ID, "offer-id")
                        .add(ODRL_ASSIGNER_ATTRIBUTE, createArrayBuilder().add(createObjectBuilder().add(ID, "assigner")))
                        .add(ODRL_TARGET_ATTRIBUTE, createArrayBuilder().add(createObjectBuilder().add(ID, "target"))))
                )
                .build();

        var result = validator.validate(input);

        assertThat(result).isSucceeded();
    }

    @Test
    void shouldFail_whenPolicyOfferIsNotValid() {
        var input = Json.createObjectBuilder()
                .add(CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS, value("http://connector-address"))
                .add(PROTOCOL, value("protocol"))
                .add(POLICY, createArrayBuilder()
                        .add(createObjectBuilder()
                                .add(TYPE, createArrayBuilder().add("wrongType"))
                        )
                )
                .build();

        var result = validator.validate(input);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .isNotEmpty()
                .allSatisfy(violation -> assertThat(violation.path()).startsWith(POLICY))
                .anySatisfy(violation -> assertThat(violation.path()).endsWith(TYPE))
                .anySatisfy(violation -> assertThat(violation.path()).endsWith(ODRL_TARGET_ATTRIBUTE))
                .anySatisfy(violation -> assertThat(violation.path()).endsWith(ODRL_ASSIGNER_ATTRIBUTE))
                .anySatisfy(violation -> assertThat(violation.path()).endsWith(ID));
    }

    @Test
    void shouldFail_whenOfferAndPolicyAreMissing() {
        var input = Json.createObjectBuilder()
                .add(CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS, value("http://connector-address"))
                .add(PROTOCOL, value("protocol"))
                .build();

        var result = validator.validate(input);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .isNotEmpty()
                .anySatisfy(violation -> assertThat(violation.message()).contains(POLICY));
    }

    @Test
    void shouldFail_whenMandatoryPropertiesAreMissing() {
        var input = Json.createObjectBuilder().build();

        var result = validator.validate(input);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .isNotEmpty()
                .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS))
                .anySatisfy(violation -> assertThat(violation.path()).isEqualTo(PROTOCOL));
    }

    @Test
    void shouldSucceed_whenDeprecatedProviderIdIsUsedWarningLogged() {
        String expectedLogMessage = format("The attribute %s has been deprecated in type %s, please use %s",
                PROVIDER_ID, CONTRACT_REQUEST_TYPE, ODRL_ASSIGNER_ATTRIBUTE);

        var input = Json.createObjectBuilder()
                .add(PROVIDER_ID, value("provider_id"))
                .build();

        validator.validate(input);

        verify(monitor).warning(expectedLogMessage);
    }

    @Deprecated(since = "0.3.2")
    @Test
    void shouldFail_whenOfferMandatoryPropertiesAreMissing() {
        var input = Json.createObjectBuilder()
                .add(CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS, value("http://connector-address"))
                .add(CONNECTOR_ADDRESS, value("http://connector-address"))
                .add(PROTOCOL, value("protocol"))
                .add(OFFER, createArrayBuilder().add(createObjectBuilder()))
                .build();

        var result = validator.validate(input);

        assertThat(result).isFailed().extracting(ValidationFailure::getViolations).asInstanceOf(list(Violation.class))
                .isNotEmpty()
                .anySatisfy(violation -> assertThat(violation.path()).contains(OFFER_ID))
                .anySatisfy(violation -> assertThat(violation.path()).contains(ASSET_ID))
                .anySatisfy(violation -> assertThat(violation.path()).contains(POLICY));
    }

    @Deprecated(since = "0.3.2")
    @Test
    void shouldSucceed_whenDeprecatedOfferIsUsed() {
        var input = Json.createObjectBuilder()
                .add(CONTRACT_REQUEST_COUNTER_PARTY_ADDRESS, value("http://connector-address"))
                .add(PROTOCOL, value("protocol"))
                .add(OFFER, createArrayBuilder().add(createObjectBuilder()
                        .add(OFFER_ID, value("offerId"))
                        .add(ASSET_ID, value("offerId"))
                        .add(POLICY, createArrayBuilder().add(createObjectBuilder()))))
                .build();

        var result = validator.validate(input);
        assertThat(result).isSucceeded();
        verify(monitor).warning(anyString());
    }

    @Deprecated(since = "0.3.2")
    @Test
    void shouldSucceed_whenDeprecatedConnectorAddressIsUsed() {
        var input = Json.createObjectBuilder()
                .add(CONNECTOR_ADDRESS, value("http://connector-address"))
                .add(PROTOCOL, value("protocol"))
                .add(POLICY, createArrayBuilder().add(createObjectBuilder()
                        .add(ID, "offer-id")
                        .add(TYPE, createArrayBuilder().add(ODRL_POLICY_TYPE_OFFER))
                        .add(ODRL_ASSIGNER_ATTRIBUTE, createArrayBuilder().add(createObjectBuilder().add(ID, "assigner")))
                        .add(ODRL_TARGET_ATTRIBUTE, createArrayBuilder().add(createObjectBuilder().add(ID, "target")))))
                .build();

        var result = validator.validate(input);
        assertThat(result).isSucceeded();
        verify(monitor).warning(anyString());
    }

    private JsonArrayBuilder value(String value) {
        return createArrayBuilder().add(createObjectBuilder().add(VALUE, value));
    }
}
