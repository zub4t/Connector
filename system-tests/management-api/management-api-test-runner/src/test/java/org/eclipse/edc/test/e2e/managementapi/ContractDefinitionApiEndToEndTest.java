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

package org.eclipse.edc.test.e2e.managementapi;

import jakarta.json.JsonArray;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import org.eclipse.edc.connector.contract.spi.offer.store.ContractDefinitionStore;
import org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition;
import org.eclipse.edc.junit.annotations.EndToEndTest;
import org.eclipse.edc.junit.annotations.PostgresqlIntegrationTest;
import org.eclipse.edc.junit.extensions.EdcRuntimeExtension;
import org.eclipse.edc.sql.testfixtures.PostgresqlEndToEndInstance;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.http.ContentType.JSON;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.LIST;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.CONTEXT;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.ID;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VOCAB;
import static org.eclipse.edc.spi.CoreConstants.EDC_NAMESPACE;
import static org.eclipse.edc.spi.query.Criterion.criterion;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

public class ContractDefinitionApiEndToEndTest {

    @Nested
    @EndToEndTest
    class InMemory extends Tests implements InMemoryRuntime {

        InMemory() {
            super(RUNTIME);
        }

    }

    @Nested
    @PostgresqlIntegrationTest
    class Postgres extends Tests implements PostgresRuntime {

        Postgres() {
            super(RUNTIME);
        }

        @BeforeAll
        static void beforeAll() {
            PostgresqlEndToEndInstance.createDatabase("runtime");
        }

    }

    abstract static class Tests extends ManagementApiEndToEndTestBase {

        Tests(EdcRuntimeExtension runtime) {
            super(runtime);
        }

        @Test
        void queryContractDefinitions_noQuerySpec() {
            var contractDefStore = getContractDefinitionStore();
            var id = UUID.randomUUID().toString();
            contractDefStore.save(createContractDefinition(id).build());

            var body = baseRequest()
                    .contentType(JSON)
                    .post("/v2/contractdefinitions/request")
                    .then()
                    .statusCode(200)
                    .body("size()", greaterThan(0))
                    .extract().body().as(JsonArray.class);

            var assetsSelector = body.stream().map(JsonValue::asJsonObject)
                    .filter(it -> it.getString(ID).equals(id))
                    .map(it -> it.getJsonArray("assetsSelector"))
                    .findAny();

            assertThat(assetsSelector).isPresent().get().asInstanceOf(LIST).hasSize(2);
        }

        @Test
        void queryPolicyDefinitionWithSimplePrivateProperties() {
            var id = UUID.randomUUID().toString();
            var requestJson = createDefinitionBuilder(id)
                    .add("privateProperties", createObjectBuilder()
                            .add("newKey", createObjectBuilder().add(ID, "newValue"))
                            .build())
                    .build();

            baseRequest()
                    .contentType(JSON)
                    .body(requestJson)
                    .post("/v2/contractdefinitions")
                    .then()
                    .statusCode(200)
                    .body("@id", equalTo(id));

            var matchingQuery = query(
                    criterion("id", "=", id),
                    criterion("privateProperties.'%snewKey'.@id".formatted(EDC_NAMESPACE), "=", "newValue")
            );

            baseRequest()
                    .body(matchingQuery)
                    .contentType(JSON)
                    .post("/v2/contractdefinitions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(1));


            var nonMatchingQuery = query(
                    criterion("id", "=", id),
                    criterion("privateProperties.'%snewKey'.@id".formatted(EDC_NAMESPACE), "=", "anything-else")
            );

            baseRequest()
                    .body(nonMatchingQuery)
                    .contentType(JSON)
                    .post("/v2/contractdefinitions/request")
                    .then()
                    .log().ifError()
                    .statusCode(200)
                    .body("size()", is(0));
        }

        @Test
        void shouldCreateAndRetrieve() {
            var id = UUID.randomUUID().toString();
            var requestJson = createDefinitionBuilder(id)
                    .build();

            baseRequest()
                    .contentType(JSON)
                    .body(requestJson)
                    .post("/v2/contractdefinitions")
                    .then()
                    .statusCode(200)
                    .body("@id", equalTo(id));

            var actual = getContractDefinitionStore().findById(id);

            assertThat(actual.getId()).matches(id);
        }

        @Test
        void delete() {
            var id = UUID.randomUUID().toString();
            var entity = createContractDefinition(id).build();
            getContractDefinitionStore().save(entity);

            baseRequest()
                    .delete("/v2/contractdefinitions/" + id)
                    .then()
                    .statusCode(204);

            var actual = getContractDefinitionStore().findById(id);

            assertThat(actual).isNull();
        }

        @Test
        void update_whenExists() {
            var store = getContractDefinitionStore();
            var id = UUID.randomUUID().toString();
            var entity = createContractDefinition(id).build();
            store.save(entity);

            var updated = createDefinitionBuilder(id)
                    .add("accessPolicyId", "new-policy")
                    .build();

            baseRequest()
                    .contentType(JSON)
                    .body(updated)
                    .put("/v2/contractdefinitions")
                    .then()
                    .statusCode(204);

            var actual = store.findById(id);

            assertThat(actual.getAccessPolicyId()).isEqualTo("new-policy");
        }

        @Test
        void update_whenNotExists() {
            var updated = createDefinitionBuilder(UUID.randomUUID().toString())
                    .add("accessPolicyId", "new-policy")
                    .build();

            baseRequest()
                    .contentType(JSON)
                    .body(updated)
                    .put("/v2/contractdefinitions")
                    .then()
                    .statusCode(404);
        }

        private ContractDefinitionStore getContractDefinitionStore() {
            return runtime.getContext().getService(ContractDefinitionStore.class);
        }

        private JsonObjectBuilder createDefinitionBuilder(String id) {
            return createObjectBuilder()
                    .add(CONTEXT, createObjectBuilder().add(VOCAB, EDC_NAMESPACE))
                    .add(TYPE, EDC_NAMESPACE + "ContractDefinition")
                    .add(ID, id)
                    .add("accessPolicyId", UUID.randomUUID().toString())
                    .add("contractPolicyId", UUID.randomUUID().toString())
                    .add("assetsSelector", createArrayBuilder()
                            .add(createCriterionBuilder("foo", "=", "bar"))
                            .add(createCriterionBuilder("bar", "=", "baz")).build());
        }

        private JsonObjectBuilder createCriterionBuilder(String left, String operator, String right) {
            return createObjectBuilder()
                    .add(TYPE, "Criterion")
                    .add("operandLeft", left)
                    .add("operator", operator)
                    .add("operandRight", right);
        }

        private ContractDefinition.Builder createContractDefinition(String id) {
            return ContractDefinition.Builder.newInstance()
                    .id(id)
                    .accessPolicyId(UUID.randomUUID().toString())
                    .contractPolicyId(UUID.randomUUID().toString())
                    .assetsSelectorCriterion(criterion("foo", "=", "bar"))
                    .assetsSelectorCriterion(criterion("bar", "=", "baz"));
        }
    }

}
