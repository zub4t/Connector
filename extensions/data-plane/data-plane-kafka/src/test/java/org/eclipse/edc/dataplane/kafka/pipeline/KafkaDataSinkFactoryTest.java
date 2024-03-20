/*
 *  Copyright (c) 2023 Amadeus
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

package org.eclipse.edc.dataplane.kafka.pipeline;

import org.eclipse.edc.dataplane.kafka.config.KafkaPropertiesFactory;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ExecutorService;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.eclipse.edc.dataaddress.kafka.spi.KafkaDataAddressSchema.BOOTSTRAP_SERVERS;
import static org.eclipse.edc.dataaddress.kafka.spi.KafkaDataAddressSchema.KAFKA_TYPE;
import static org.eclipse.edc.dataaddress.kafka.spi.KafkaDataAddressSchema.TOPIC;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaDataSinkFactoryTest {

    private final KafkaPropertiesFactory propertiesFactory = mock(KafkaPropertiesFactory.class);

    private KafkaDataSinkFactory factory;

    @BeforeEach
    public void setUp() {
        factory = new KafkaDataSinkFactory(mock(ExecutorService.class), mock(Monitor.class), propertiesFactory, 1);
    }

    @Test
    void verifyCanHandle() {
        assertThat(factory.canHandle(createRequest("kafka", emptyMap()))).isTrue();
        assertThat(factory.canHandle(createRequest("KaFka", emptyMap()))).isTrue();
        assertThat(factory.canHandle(createRequest("kafkax", emptyMap()))).isFalse();
    }

    @Test
    void verifyValidateSuccess() {
        var request = createRequest(KAFKA_TYPE, Map.of(TOPIC, "test", BOOTSTRAP_SERVERS, "any:9183"));

        when(propertiesFactory.getProducerProperties(request.getDestinationDataAddress().getProperties()))
                .thenReturn(Result.success(mock(Properties.class)));

        var result = factory.validateRequest(request);

        assertThat(result.succeeded()).isTrue();
    }

    @Test
    void verifyValidateReturnsFailedResult_ifMissingTopicProperty() {
        var request = createRequest(KAFKA_TYPE, emptyMap());

        when(propertiesFactory.getProducerProperties(request.getDestinationDataAddress().getProperties()))
                .thenReturn(Result.success(mock(Properties.class)));

        var result = factory.validateRequest(request);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureDetail()).contains("topic");
    }

    @Test
    void verifyValidateReturnsFailedResult_ifKafkaPropertiesFactoryFails() {
        var errorMsg = "test-error";
        var request = createRequest(KAFKA_TYPE, Map.of(TOPIC, "test"));

        when(propertiesFactory.getProducerProperties(request.getDestinationDataAddress().getProperties()))
                .thenReturn(Result.failure(errorMsg));

        var result = factory.validateRequest(request);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.getFailureDetail()).contains("kafka.bootstrap.servers");
    }

    @Test
    void verifyCreateSinkThrows_ifMissingTopicProperty() {
        var request = createRequest(KAFKA_TYPE, emptyMap());

        when(propertiesFactory.getProducerProperties(request.getDestinationDataAddress().getProperties()))
                .thenReturn(Result.success(mock(Properties.class)));

        assertThatExceptionOfType(EdcException.class).isThrownBy(() -> factory.createSink(request));
    }

    @Test
    void verifyCreateSinkThrows_ifKafkaPropertiesFactoryFails() {
        var errorMsg = "test-error";
        var request = createRequest(KAFKA_TYPE, Map.of(TOPIC, "test"));

        when(propertiesFactory.getProducerProperties(request.getDestinationDataAddress().getProperties()))
                .thenReturn(Result.failure(errorMsg));

        assertThatExceptionOfType(EdcException.class).isThrownBy(() -> factory.createSink(request));
    }

    private DataFlowStartMessage createRequest(String destinationType, Map<String, Object> destinationProperties) {
        return DataFlowStartMessage.Builder.newInstance()
                .id("id")
                .processId("processId")
                .destinationDataAddress(DataAddress.Builder.newInstance()
                        .type(destinationType)
                        .properties(destinationProperties)
                        .build())
                .sourceDataAddress(DataAddress.Builder.newInstance().type("notused").build())
                .build();
    }
}
