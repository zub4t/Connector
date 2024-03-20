/*
 *  Copyright (c) 2024 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
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

package org.eclipse.edc.test.e2e;

import io.netty.handler.codec.http.HttpMethod;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.eclipse.edc.connector.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.extensions.EdcClassRuntimesExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import javax.validation.constraints.NotNull;

import static java.lang.String.format;
import static java.time.Duration.ZERO;
import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.eclipse.edc.connector.transfer.spi.types.TransferProcessStates.SUSPENDED;
import static org.eclipse.edc.connector.transfer.spi.types.TransferProcessStates.TERMINATED;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.junit.testfixtures.TestUtils.getFreePort;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.test.e2e.Runtimes.backendService;
import static org.eclipse.edc.test.e2e.Runtimes.controlPlaneSignaling;
import static org.eclipse.edc.test.e2e.Runtimes.dataPlane;
import static org.eclipse.edc.test.system.utils.PolicyFixtures.contractExpiresIn;
import static org.eclipse.edc.test.system.utils.PolicyFixtures.noConstraintPolicy;
import static org.mockserver.integration.ClientAndServer.startClientAndServer;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.never;

public class TransferStreamingEndToEndTest {

    @Nested
    @EndToEndTest
    class InMemory extends Tests {

        @RegisterExtension
        static final EdcClassRuntimesExtension RUNTIMES = new EdcClassRuntimesExtension(
                controlPlaneSignaling("consumer-control-plane", CONSUMER.controlPlaneConfiguration()),
                backendService("consumer-backend-service", Map.of("web.http.port", String.valueOf(CONSUMER.backendService().getPort()))),
                controlPlaneSignaling("provider-control-plane", PROVIDER.controlPlaneConfiguration()),
                dataPlane("provider-data-plane", PROVIDER.dataPlaneConfiguration()),
                backendService("provider-backend-service", Map.of("web.http.port", String.valueOf(PROVIDER.backendService().getPort())))
        );

    }

    @Testcontainers
    abstract static class Tests extends TransferEndToEndTestBase {

        @Container
        private static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

        private static final String SOURCE_TOPIC = "source_topic";
        private static final String SINK_TOPIC = "sink_topic";

        @BeforeEach
        void setUp() {
            var producer = createKafkaProducer();

            newSingleThreadScheduledExecutor().scheduleAtFixedRate(
                    () -> producer.send(new ProducerRecord<>(SOURCE_TOPIC, sampleMessage())),
                    0, 100, MILLISECONDS);
        }

        @Test
        void kafkaToHttpTransfer() {
            var destinationServer = startClientAndServer(getFreePort());
            var request = request()
                    .withMethod(HttpMethod.POST.name())
                    .withPath("/api/service");
            destinationServer.when(request).respond(response());
            PROVIDER.registerDataPlane(Set.of("HttpData-PUSH"));

            var assetId = UUID.randomUUID().toString();
            createResourcesOnProvider(assetId, contractExpiresIn("10s"), kafkaSourceProperty());

            var destination = httpSink(destinationServer.getLocalPort(), "/api/service");
            var transferProcessId = CONSUMER.requestAsset(PROVIDER, assetId, noPrivateProperty(), destination, "HttpData-PUSH");

            await().atMost(timeout).untilAsserted(() -> {
                destinationServer.verify(request, atLeast(1));
            });

            await().atMost(timeout).untilAsserted(() -> {
                var state = CONSUMER.getTransferProcessState(transferProcessId);
                assertThat(TransferProcessStates.valueOf(state)).isGreaterThanOrEqualTo(TERMINATED);
            });

            destinationServer.clear(request)
                    .when(request).respond(response());
            await().pollDelay(5, SECONDS).atMost(timeout).untilAsserted(() -> {
                try {
                    destinationServer.verify(request, never());
                } catch (AssertionError assertionError) {
                    destinationServer.clear(request)
                            .when(request).respond(response());
                    throw assertionError;
                }
            });

            stopQuietly(destinationServer);
        }

        @Test
        void kafkaToKafkaTransfer() {
            try (var consumer = createKafkaConsumer()) {
                consumer.subscribe(List.of(SINK_TOPIC));

                PROVIDER.registerDataPlane(Set.of("Kafka-PUSH"));

                var assetId = UUID.randomUUID().toString();
                createResourcesOnProvider(assetId, contractExpiresIn("10s"), kafkaSourceProperty());

                var transferProcessId = CONSUMER.requestAsset(PROVIDER, assetId, noPrivateProperty(), kafkaSink(), "Kafka-PUSH");
                await().atMost(timeout).untilAsserted(() -> {
                    var records = consumer.poll(ZERO);
                    assertThat(records.isEmpty()).isFalse();
                    records.records(SINK_TOPIC).forEach(record -> assertThat(record.value()).isEqualTo(sampleMessage()));
                });

                await().atMost(timeout).untilAsserted(() -> {
                    var state = CONSUMER.getTransferProcessState(transferProcessId);
                    assertThat(TransferProcessStates.valueOf(state)).isGreaterThanOrEqualTo(TERMINATED);
                });

                consumer.poll(ZERO);
                await().pollDelay(5, SECONDS).atMost(timeout).untilAsserted(() -> {
                    var recordsFound = consumer.poll(Duration.ofSeconds(1)).records(SINK_TOPIC);
                    assertThat(recordsFound).isEmpty();
                });
            }
        }

        @Test
        void shouldSuspendTransfer() {
            try (var consumer = createKafkaConsumer()) {
                consumer.subscribe(List.of(SINK_TOPIC));

                PROVIDER.registerDataPlane(Set.of("Kafka-PUSH"));

                var assetId = UUID.randomUUID().toString();
                createResourcesOnProvider(assetId, noConstraintPolicy(), kafkaSourceProperty());

                var transferProcessId = CONSUMER.requestAsset(PROVIDER, assetId, noPrivateProperty(), kafkaSink(), "Kafka-PUSH");
                await().atMost(timeout).untilAsserted(() -> {
                    var records = consumer.poll(ZERO);
                    assertThat(records.isEmpty()).isFalse();
                    records.records(SINK_TOPIC).forEach(record -> assertThat(record.value()).isEqualTo(sampleMessage()));
                });

                CONSUMER.suspendTransfer(transferProcessId, "any kind of reason");

                await().atMost(timeout).untilAsserted(() -> {
                    var state = CONSUMER.getTransferProcessState(transferProcessId);
                    assertThat(TransferProcessStates.valueOf(state)).isGreaterThanOrEqualTo(SUSPENDED);
                });

                consumer.poll(ZERO);
                await().pollDelay(5, SECONDS).atMost(timeout).untilAsserted(() -> {
                    var recordsFound = consumer.poll(Duration.ofSeconds(1)).records(SINK_TOPIC);
                    assertThat(recordsFound).isEmpty();
                });
            }
        }

        private JsonObject httpSink(Integer port, String path) {
            return Json.createObjectBuilder()
                    .add(TYPE, EDC_NAMESPACE + "DataAddress")
                    .add(EDC_NAMESPACE + "type", "HttpData")
                    .add(EDC_NAMESPACE + "properties", Json.createObjectBuilder()
                            .add(EDC_NAMESPACE + "name", "data")
                            .add(EDC_NAMESPACE + "baseUrl", format("http://localhost:%s", port))
                            .add(EDC_NAMESPACE + "path", path)
                            .build())
                    .build();
        }

        @NotNull
        private JsonObject kafkaSink() {
            return Json.createObjectBuilder()
                    .add(TYPE, EDC_NAMESPACE + "DataAddress")
                    .add(EDC_NAMESPACE + "type", "Kafka")
                    .add(EDC_NAMESPACE + "properties", Json.createObjectBuilder()
                            .add(EDC_NAMESPACE + "topic", SINK_TOPIC)
                            .add(EDC_NAMESPACE + kafkaProperty("bootstrap.servers"), KAFKA.getBootstrapServers())
                            .build())
                    .build();
        }

        @NotNull
        private Map<String, Object> kafkaSourceProperty() {
            return Map.of(
                    "name", "data",
                    "type", "Kafka",
                    "topic", SOURCE_TOPIC,
                    kafkaProperty("bootstrap.servers"), KAFKA.getBootstrapServers(),
                    kafkaProperty("max.poll.records"), "100"
            );
        }

        private Consumer<String, String> createKafkaConsumer() {
            var props = new Properties();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "runner");
            props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
            return new KafkaConsumer<>(props);
        }

        private Producer<String, String> createKafkaProducer() {
            var props = new Properties();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
            return new KafkaProducer<>(props);
        }

        private String kafkaProperty(String property) {
            return "kafka." + property;
        }

        private JsonObject noPrivateProperty() {
            return Json.createObjectBuilder().build();
        }

        private String sampleMessage() {
            return "sampleMessage";
        }
    }

}
