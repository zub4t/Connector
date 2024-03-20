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

package org.eclipse.edc.connector.service.contractnegotiation;

import org.eclipse.edc.connector.contract.spi.negotiation.ConsumerContractNegotiationManager;
import org.eclipse.edc.connector.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.contract.spi.types.command.TerminateNegotiationCommand;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiation;
import org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest;
import org.eclipse.edc.connector.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.policy.model.Policy;
import org.eclipse.edc.spi.command.CommandHandlerRegistry;
import org.eclipse.edc.spi.command.CommandResult;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.response.StatusResult;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.types.domain.agreement.ContractAgreement;
import org.eclipse.edc.spi.types.domain.offer.ContractOffer;
import org.eclipse.edc.transaction.spi.NoopTransactionContext;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractNegotiationStates.REQUESTED;
import static org.eclipse.edc.junit.assertions.AbstractResultAssert.assertThat;
import static org.eclipse.edc.spi.query.Criterion.criterion;
import static org.eclipse.edc.spi.result.ServiceFailure.Reason.NOT_FOUND;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ContractNegotiationServiceImplTest {

    private final ContractNegotiationStore store = mock();
    private final ConsumerContractNegotiationManager consumerManager = mock();
    private final CommandHandlerRegistry commandHandlerRegistry = mock();
    private final TransactionContext transactionContext = new NoopTransactionContext();

    private final ContractNegotiationService service = new ContractNegotiationServiceImpl(store, consumerManager, transactionContext, commandHandlerRegistry);

    @Test
    void findById_filtersById() {
        var negotiation = createContractNegotiation("negotiationId");
        when(store.findById("negotiationId")).thenReturn(negotiation);

        var result = service.findbyId("negotiationId");

        assertThat(result).matches(it -> it.getId().equals("negotiationId"));
    }

    @Test
    void findById_returnsNullIfNotFound() {
        when(store.findById("negotiationId")).thenReturn(null);

        var result = service.findbyId("negotiationId");

        assertThat(result).isNull();
    }

    @Test
    void search_filtersBySpec() {
        var negotiation = createContractNegotiation("negotiationId");
        when(store.queryNegotiations(isA(QuerySpec.class))).thenReturn(Stream.of(negotiation));

        var result = service.search(QuerySpec.none());

        assertThat(result.succeeded()).isTrue();
        assertThat(result.getContent()).hasSize(1).first().matches(it -> it.getId().equals("negotiationId"));
    }

    @ParameterizedTest
    @ArgumentsSource(InvalidFilters.class)
    void search_invalidFilter(Criterion invalidFilter) {
        var query = QuerySpec.Builder.newInstance()
                .filter(invalidFilter)
                .build();

        var result = service.search(query);

        assertThat(result).isFailed();
    }

    @ParameterizedTest
    @ArgumentsSource(ValidFilters.class)
    void search_validFilter(Criterion validFilter) {
        var query = QuerySpec.Builder.newInstance()
                .filter(validFilter)
                .build();

        var result = service.search(query);

        assertThat(result).isSucceeded();
        verify(store).queryNegotiations(query);
    }

    @Test
    void getState_returnsStringRepresentation() {
        var negotiation = createContractNegotiationBuilder("negotiationId")
                .state(REQUESTED.code())
                .build();
        when(store.findById("negotiationId")).thenReturn(negotiation);

        var result = service.getState("negotiationId");

        assertThat(result).isEqualTo(REQUESTED.name());
    }

    @Test
    void getState_returnsNullIfNegotiationDoesNotExist() {
        when(store.findById("negotiationId")).thenReturn(null);

        var result = service.getState("negotiationId");

        assertThat(result).isNull();
    }

    @Test
    void getForNegotiation_filtersById() {
        var contractAgreement = createContractAgreement("agreementId");
        var negotiation = createContractNegotiation("negotiationId");
        negotiation.setContractAgreement(contractAgreement);

        when(store.findById("negotiationId")).thenReturn(negotiation);

        var result = service.getForNegotiation("negotiationId");

        assertThat(result).matches(it -> it.getId().equals("agreementId"));
        verify(store).findById(any());
        verifyNoMoreInteractions(store);
    }

    @Test
    void getForNegotiation_negotiationNotFound() {
        when(store.findById("negotiationId")).thenReturn(null);
        var result = service.getForNegotiation("negotiationId");
        assertThat(result).isNull();
        verify(store).findById(any());
        verifyNoMoreInteractions(store);
    }

    @Test
    void getForNegotiation_negotiationNoAgreement() {
        var negotiation = createContractNegotiation("negotiationId");
        when(store.findById("negotiationId")).thenReturn(negotiation);

        var result = service.getForNegotiation("negotiationId");

        assertThat(result).isNull();
        verify(store).findById(any());
        verifyNoMoreInteractions(store);
    }

    @Test
    void getForNegotiation_returnsNullIfNotFound() {
        when(store.findContractAgreement("agreementId")).thenReturn(null);

        var result = service.getForNegotiation("agreementId");

        assertThat(result).isNull();
    }

    @Test
    void initiateNegotiation_callsManager() {
        var contractNegotiation = createContractNegotiation("negotiationId");
        when(consumerManager.initiate(isA(ContractRequest.class))).thenReturn(StatusResult.success(contractNegotiation));

        var request = ContractRequest.Builder.newInstance()
                .counterPartyAddress("address")
                .protocol("protocol")
                .contractOffer(createContractOffer())
                .build();

        var result = service.initiateNegotiation(request);

        assertThat(result).matches(it -> it.getId().equals("negotiationId"));
    }

    @Test
    void terminate_shouldExecuteCommand() {
        var negotiation = createContractNegotiation("negotiationId");
        when(store.findById("negotiationId")).thenReturn(negotiation);
        when(commandHandlerRegistry.execute(any())).thenReturn(CommandResult.success());
        var command = new TerminateNegotiationCommand("negotiationId", "reason");

        var result = service.terminate(command);

        assertThat(result).isSucceeded();
        verify(commandHandlerRegistry).execute(command);
    }

    @Test
    void terminate_shouldNotCancelNegationIfItDoesNotExist() {
        when(commandHandlerRegistry.execute(any())).thenReturn(CommandResult.notFound("not found"));
        var command = new TerminateNegotiationCommand("negotiationId", "reason");

        var result = service.terminate(command);

        assertThat(result).isFailed().extracting(ServiceFailure::getReason).isEqualTo(NOT_FOUND);
    }

    private static class InvalidFilters implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    arguments(criterion("contractAgreement.contractStartDate.begin", "=", "123455")), // invalid path
                    arguments(criterion("contractOffers.policy.unexistent", "=", "123455")), // invalid path
                    arguments(criterion("contractOffers.policy.assetid", "=", "123455")), // wrong case
                    arguments(criterion("contractOffers.policy.=some-id", "=", "123455")) // incomplete path
            );
        }
    }

    private static class ValidFilters implements ArgumentsProvider {
        @Override
        public Stream<? extends Arguments> provideArguments(ExtensionContext context) {
            return Stream.of(
                    arguments(criterion("contractAgreement.assetId", "=", "test-asset")),
                    arguments(criterion("contractAgreement.policy.assignee", "=", "123455"))
            );
        }
    }

    private ContractNegotiation createContractNegotiation(String negotiationId) {
        return createContractNegotiationBuilder(negotiationId)
                .build();
    }

    private ContractAgreement createContractAgreement(String agreementId) {
        return ContractAgreement.Builder.newInstance()
                .id(agreementId)
                .providerId(UUID.randomUUID().toString())
                .consumerId(UUID.randomUUID().toString())
                .assetId(UUID.randomUUID().toString())
                .policy(Policy.Builder.newInstance().build())
                .build();
    }

    private ContractNegotiation.Builder createContractNegotiationBuilder(String negotiationId) {
        return ContractNegotiation.Builder.newInstance()
                .id(negotiationId)
                .counterPartyId(UUID.randomUUID().toString())
                .counterPartyAddress("address")
                .protocol("protocol");
    }

    private ContractOffer createContractOffer() {
        return ContractOffer.Builder.newInstance()
                .id(UUID.randomUUID().toString())
                .policy(Policy.Builder.newInstance().build())
                .assetId("test-asset")
                .build();
    }
}
