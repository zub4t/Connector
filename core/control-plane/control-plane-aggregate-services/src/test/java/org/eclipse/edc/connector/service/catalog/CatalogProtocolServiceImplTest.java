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
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.connector.service.catalog;

import org.eclipse.edc.catalog.spi.CatalogRequestMessage;
import org.eclipse.edc.catalog.spi.DataService;
import org.eclipse.edc.catalog.spi.DataServiceRegistry;
import org.eclipse.edc.catalog.spi.Dataset;
import org.eclipse.edc.catalog.spi.DatasetResolver;
import org.eclipse.edc.catalog.spi.Distribution;
import org.eclipse.edc.connector.spi.protocol.ProtocolTokenValidator;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.agent.ParticipantAgent;
import org.eclipse.edc.spi.iam.TokenRepresentation;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.service.catalog.CatalogProtocolServiceImpl.CATALOGING_REQUEST_SCOPE;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.result.ServiceFailure.Reason.NOT_FOUND;
import static org.eclipse.edc.spi.result.ServiceFailure.Reason.UNAUTHORIZED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CatalogProtocolServiceImplTest {

    private final DatasetResolver datasetResolver = mock();
    private final DataServiceRegistry dataServiceRegistry = mock();
    private final ProtocolTokenValidator protocolTokenValidator = mock();
    private final TransactionContext transactionContext = spy(new NoopTransactionContext());

    private final CatalogProtocolServiceImpl service = new CatalogProtocolServiceImpl(datasetResolver,
            dataServiceRegistry, protocolTokenValidator, "participantId", transactionContext);

    @Nested
    class GetCatalog {

        @Test
        void shouldReturnCatalogWithConnectorDataServiceAndItsDataset() {
            var querySpec = QuerySpec.none();
            var message = CatalogRequestMessage.Builder.newInstance().protocol("protocol").querySpec(querySpec).build();
            var tokenRepresentation = createTokenRepresentation();
            var participantAgent = createParticipantAgent();
            var dataService = DataService.Builder.newInstance().build();

            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CATALOGING_REQUEST_SCOPE))).thenReturn(ServiceResult.success(participantAgent));
            when(dataServiceRegistry.getDataServices()).thenReturn(List.of(dataService));
            when(datasetResolver.query(any(), any())).thenReturn(Stream.of(createDataset()));

            var result = service.getCatalog(message, tokenRepresentation);

            assertThat(result).isSucceeded().satisfies(catalog -> {
                assertThat(catalog.getDataServices()).hasSize(1).first().isSameAs(dataService);
                assertThat(catalog.getDatasets()).hasSize(1);
                assertThat(catalog.getParticipantId()).isEqualTo("participantId");
            });
            verify(datasetResolver).query(eq(participantAgent), eq(querySpec));
            verify(transactionContext).execute(any(TransactionContext.ResultTransactionBlock.class));
        }

        @Test
        void shouldFail_whenTokenValidationFails() {
            var querySpec = QuerySpec.none();
            var message = CatalogRequestMessage.Builder.newInstance().protocol("protocol").querySpec(querySpec).build();
            var tokenRepresentation = createTokenRepresentation();

            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CATALOGING_REQUEST_SCOPE))).thenReturn(ServiceResult.unauthorized("unauthorized"));

            var result = service.getCatalog(message, tokenRepresentation);

            assertThat(result).isFailed().extracting(ServiceFailure::getReason).isEqualTo(UNAUTHORIZED);
        }
    }

    @Nested
    class GetDataset {

        @Test
        void shouldReturnDataset() {
            var tokenRepresentation = createTokenRepresentation();
            var participantAgent = createParticipantAgent();
            var dataset = createDataset();

            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CATALOGING_REQUEST_SCOPE))).thenReturn(ServiceResult.success(participantAgent));
            when(datasetResolver.getById(any(), any())).thenReturn(dataset);

            var result = service.getDataset("datasetId", tokenRepresentation);

            assertThat(result).isSucceeded().isEqualTo(dataset);
            verify(datasetResolver).getById(participantAgent, "datasetId");
            verify(transactionContext).execute(any(TransactionContext.ResultTransactionBlock.class));
        }

        @Test
        void shouldFail_whenDatasetIsNull() {
            var participantAgent = createParticipantAgent();
            var tokenRepresentation = createTokenRepresentation();

            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CATALOGING_REQUEST_SCOPE))).thenReturn(ServiceResult.success(participantAgent));
            when(datasetResolver.getById(any(), any())).thenReturn(null);

            var result = service.getDataset("datasetId", tokenRepresentation);

            assertThat(result).isFailed().extracting(ServiceFailure::getReason).isEqualTo(NOT_FOUND);
        }

        @Test
        void shouldFail_whenTokenValidationFails() {
            var tokenRepresentation = createTokenRepresentation();

            when(protocolTokenValidator.verify(eq(tokenRepresentation), eq(CATALOGING_REQUEST_SCOPE))).thenReturn(ServiceResult.unauthorized("unauthorized"));

            var result = service.getDataset("datasetId", tokenRepresentation);

            assertThat(result).isFailed().extracting(ServiceFailure::getReason).isEqualTo(UNAUTHORIZED);
        }
    }


    private ParticipantAgent createParticipantAgent() {
        return new ParticipantAgent(emptyMap(), emptyMap());
    }

    private Dataset createDataset() {
        var dataService = DataService.Builder.newInstance().build();
        var distribution = Distribution.Builder.newInstance().dataService(dataService).format("any").build();
        return Dataset.Builder.newInstance()
                .offer(UUID.randomUUID().toString(), Policy.Builder.newInstance().build())
                .distribution(distribution)
                .build();
    }

    private TokenRepresentation createTokenRepresentation() {
        return TokenRepresentation.Builder.newInstance().build();
    }
}
