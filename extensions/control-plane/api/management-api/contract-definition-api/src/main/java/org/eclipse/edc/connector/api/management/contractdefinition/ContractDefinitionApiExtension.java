/*
 *  Copyright (c) 2020 - 2022 Microsoft Corporation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Microsoft Corporation - initial API and implementation
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - improvements
 *       SAP SE - add private properties to contract definition
 *
 */

package org.eclipse.edc.connector.api.management.contractdefinition;

import jakarta.json.Json;
import org.eclipse.edc.connector.api.management.configuration.ManagementApiConfiguration;
import org.eclipse.edc.connector.api.management.contractdefinition.transform.JsonObjectFromContractDefinitionTransformer;
import org.eclipse.edc.connector.api.management.contractdefinition.transform.JsonObjectToContractDefinitionTransformer;
import org.eclipse.edc.connector.api.management.contractdefinition.validation.ContractDefinitionValidator;
import org.eclipse.edc.connector.spi.contractdefinition.ContractDefinitionService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.validator.spi.JsonObjectValidatorRegistry;
import org.eclipse.edc.web.spi.WebService;

import java.util.Map;

import static org.eclipse.edc.connector.contract.spi.types.offer.ContractDefinition.CONTRACT_DEFINITION_TYPE;
import static org.eclipse.edc.spi.CoreConstants.JSON_LD;

@Extension(value = ContractDefinitionApiExtension.NAME)
public class ContractDefinitionApiExtension implements ServiceExtension {

    public static final String NAME = "Management API: Contract Definition";

    @Inject
    WebService webService;

    @Inject
    ManagementApiConfiguration config;

    @Inject
    TypeTransformerRegistry transformerRegistry;

    @Inject
    ContractDefinitionService service;

    @Inject
    JsonObjectValidatorRegistry validatorRegistry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private CriterionOperatorRegistry criterionOperatorRegistry;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var jsonFactory = Json.createBuilderFactory(Map.of());
        var mapper = typeManager.getMapper(JSON_LD);
        transformerRegistry.register(new JsonObjectFromContractDefinitionTransformer(jsonFactory, mapper));
        transformerRegistry.register(new JsonObjectToContractDefinitionTransformer());

        validatorRegistry.register(CONTRACT_DEFINITION_TYPE, ContractDefinitionValidator.instance(criterionOperatorRegistry));

        var monitor = context.getMonitor();
        var managementApiTransformerRegistry = transformerRegistry.forContext("management-api");

        webService.registerResource(config.getContextAlias(), new ContractDefinitionApiController(
                managementApiTransformerRegistry, service, monitor, validatorRegistry));
    }
}
