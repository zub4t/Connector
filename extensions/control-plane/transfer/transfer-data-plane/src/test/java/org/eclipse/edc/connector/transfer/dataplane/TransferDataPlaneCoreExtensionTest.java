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
 *       Amadeus - Initial implementation
 *
 */

package org.eclipse.edc.connector.transfer.dataplane;

import org.eclipse.edc.connector.api.control.configuration.ControlApiConfiguration;
import org.eclipse.edc.connector.transfer.dataplane.api.ConsumerPullTransferTokenValidationApiController;
import org.eclipse.edc.connector.transfer.dataplane.flow.ConsumerPullTransferDataFlowController;
import org.eclipse.edc.connector.transfer.dataplane.flow.ProviderPushTransferDataFlowController;
import org.eclipse.edc.connector.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.junit.extensions.DependencyInjectionExtension;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.security.Vault;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.configuration.Config;
import org.eclipse.edc.web.spi.WebService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.util.Objects;

import static org.eclipse.edc.connector.transfer.dataplane.TransferDataPlaneCoreExtension.TOKEN_SIGNER_PRIVATE_KEY_ALIAS;
import static org.eclipse.edc.connector.transfer.dataplane.TransferDataPlaneCoreExtension.TOKEN_VERIFIER_PUBLIC_KEY_ALIAS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(DependencyInjectionExtension.class)
class TransferDataPlaneCoreExtensionTest {

    private static final String CONTROL_PLANE_API_CONTEXT = "control";

    private final Vault vault = mock();
    private final WebService webService = mock();
    private final DataFlowManager dataFlowManager = mock();
    private final Monitor monitor = mock();

    @BeforeEach
    public void setUp(ServiceExtensionContext context) {
        var controlApiConfigurationMock = mock(ControlApiConfiguration.class);
        when(controlApiConfigurationMock.getContextAlias()).thenReturn(CONTROL_PLANE_API_CONTEXT);

        context.registerService(WebService.class, webService);
        context.registerService(DataFlowManager.class, dataFlowManager);
        context.registerService(ControlApiConfiguration.class, controlApiConfigurationMock);
        context.registerService(Vault.class, vault);

        when(context.getMonitor()).thenReturn(monitor);
    }

    @Test
    void verifyInitializeSuccess(TransferDataPlaneCoreExtension extension, ServiceExtensionContext context) throws IOException {
        var publicKeyAlias = "publicKey";
        var privateKeyAlias = "privateKey";
        var config = mock(Config.class);
        when(context.getConfig()).thenReturn(config);
        when(config.getString(TOKEN_VERIFIER_PUBLIC_KEY_ALIAS, null)).thenReturn(publicKeyAlias);
        when(config.getString(TOKEN_SIGNER_PRIVATE_KEY_ALIAS, null)).thenReturn(privateKeyAlias);
        when(vault.resolveSecret(publicKeyAlias)).thenReturn(publicKeyPem());

        extension.initialize(context);

        verify(dataFlowManager).register(any(ConsumerPullTransferDataFlowController.class));
        verify(dataFlowManager).register(any(ProviderPushTransferDataFlowController.class));
        verify(webService).registerResource(eq(CONTROL_PLANE_API_CONTEXT), any(ConsumerPullTransferTokenValidationApiController.class));
    }

    @Test
    void shouldNotRegisterConsumerPullControllers_whenSettingsAreMissing(TransferDataPlaneCoreExtension extension, ServiceExtensionContext context) {
        var config = mock(Config.class);
        when(context.getConfig()).thenReturn(config);
        when(config.getString(TOKEN_VERIFIER_PUBLIC_KEY_ALIAS, null)).thenReturn(null);
        when(config.getString(TOKEN_SIGNER_PRIVATE_KEY_ALIAS, null)).thenReturn(null);

        extension.initialize(context);

        verify(dataFlowManager, never()).register(isA(ConsumerPullTransferDataFlowController.class));
        verifyNoInteractions(webService);
        verify(monitor).info(any(String.class));
    }

    private String publicKeyPem() throws IOException {
        try (var resource = TransferDataPlaneCoreExtensionTest.class.getClassLoader().getResourceAsStream("rsa-pubkey.pem")) {
            return new String(Objects.requireNonNull(resource).readAllBytes());
        }
    }
}
