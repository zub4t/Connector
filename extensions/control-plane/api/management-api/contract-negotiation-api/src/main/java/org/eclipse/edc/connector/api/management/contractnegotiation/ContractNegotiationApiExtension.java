/*
 *  Copyright (c) 2022 ZF Friedrichshafen AG
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       ZF Friedrichshafen AG - Initial API and Implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *
 */

package org.eclipse.edc.connector.api.management.contractnegotiation;

import jakarta.json.Json;
import org.eclipse.edc.connector.api.management.configuration.ManagementApiConfiguration;
import org.eclipse.edc.connector.api.management.contractnegotiation.transform.JsonObjectFromContractNegotiationTransformer;
import org.eclipse.edc.connector.api.management.contractnegotiation.transform.JsonObjectFromNegotiationStateTransformer;
import org.eclipse.edc.connector.api.management.contractnegotiation.transform.JsonObjectToContractOfferDescriptionTransformer;
import org.eclipse.edc.connector.api.management.contractnegotiation.transform.JsonObjectToContractOfferTransformer;
import org.eclipse.edc.connector.api.management.contractnegotiation.transform.JsonObjectToContractRequestTransformer;
import org.eclipse.edc.connector.api.management.contractnegotiation.transform.JsonObjectToTerminateNegotiationCommandTransformer;
import org.eclipse.edc.connector.api.management.contractnegotiation.validation.ContractRequestValidator;
import org.eclipse.edc.connector.api.management.contractnegotiation.validation.TerminateNegotiationValidator;
import org.eclipse.edc.connector.spi.contractnegotiation.ContractNegotiationService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.WebService;

import java.util.Map;

import static org.eclipse.edc.connector.contract.spi.types.command.TerminateNegotiationCommand.TERMINATE_NEGOTIATION_TYPE;
import static org.eclipse.edc.connector.contract.spi.types.negotiation.ContractRequest.CONTRACT_REQUEST_TYPE;

@Extension(value = ContractNegotiationApiExtension.NAME)
public class ContractNegotiationApiExtension implements ServiceExtension {

    public static final String NAME = "Management API: Contract Negotiation";

    @Inject
    private WebService webService;

    @Inject
    private ManagementApiConfiguration config;

    @Inject
    private TypeTransformerRegistry transformerRegistry;

    @Inject
    private ContractNegotiationService service;

    @Inject
    private JsonObjectValidatorRegistry validatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var factory = Json.createBuilderFactory(Map.of());
        var monitor = context.getMonitor();

        var managementApiTransformerRegistry = transformerRegistry.forContext("management-api");

        managementApiTransformerRegistry.register(new JsonObjectToContractRequestTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToContractOfferTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToContractOfferDescriptionTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToTerminateNegotiationCommandTransformer());
        managementApiTransformerRegistry.register(new JsonObjectFromContractNegotiationTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromNegotiationStateTransformer(factory));

        validatorRegistry.register(CONTRACT_REQUEST_TYPE, ContractRequestValidator.instance(monitor));
        validatorRegistry.register(TERMINATE_NEGOTIATION_TYPE, TerminateNegotiationValidator.instance());

        var controller = new ContractNegotiationApiController(service, managementApiTransformerRegistry, monitor, validatorRegistry);
        webService.registerResource(config.getContextAlias(), controller);
    }
}
