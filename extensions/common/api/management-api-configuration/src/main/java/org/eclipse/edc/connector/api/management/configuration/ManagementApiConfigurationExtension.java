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
 *
 */

package org.eclipse.edc.connector.api.management.configuration;

import jakarta.json.Json;
import org.eclipse.edc.api.auth.spi.AuthenticationRequestFilter;
import org.eclipse.edc.api.auth.spi.AuthenticationService;
import org.eclipse.edc.connector.api.management.configuration.transform.JsonObjectFromContractAgreementTransformer;
import org.eclipse.edc.core.transform.transformer.edc.from.JsonObjectFromAssetTransformer;
import org.eclipse.edc.core.transform.transformer.edc.from.JsonObjectFromCriterionTransformer;
import org.eclipse.edc.core.transform.transformer.edc.from.JsonObjectFromDataAddressTransformer;
import org.eclipse.edc.core.transform.transformer.edc.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonObjectToAssetTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonObjectToDataAddressTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.core.transform.transformer.odrl.OdrlTransformersFactory;
import org.eclipse.edc.core.transform.transformer.odrl.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.agent.ParticipantIdMapper;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.web.jersey.jsonld.JerseyJsonLdInterceptor;
import org.eclipse.edc.web.jersey.jsonld.ObjectMapperProvider;
import org.eclipse.edc.web.spi.WebServer;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.WebServiceConfigurer;
import org.eclipse.edc.web.spi.configuration.WebServiceSettings;

import java.util.Map;

import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.spi.CoreConstants.JSON_LD;

/**
 * Tells all the Management API controllers under which context alias they need to register their resources: either `default` or `management`
 */
@Provides(ManagementApiConfiguration.class)
@Extension(value = ManagementApiConfigurationExtension.NAME)
public class ManagementApiConfigurationExtension implements ServiceExtension {

    public static final String NAME = "Management API configuration";
    public static final String WEB_SERVICE_NAME = "Management API";
    private static final String MANAGEMENT_CONTEXT_ALIAS = "management";
    private static final int DEFAULT_MANAGEMENT_API_PORT = 8181;
    private static final String DEFAULT_MANAGEMENT_API_CONTEXT_PATH = "/api/v1/management";
    public static final WebServiceSettings SETTINGS = WebServiceSettings.Builder.newInstance()
            .apiConfigKey("web.http." + MANAGEMENT_CONTEXT_ALIAS)
            .contextAlias(MANAGEMENT_CONTEXT_ALIAS)
            .defaultPath(DEFAULT_MANAGEMENT_API_CONTEXT_PATH)
            .defaultPort(DEFAULT_MANAGEMENT_API_PORT)
            .useDefaultContext(true)
            .name(WEB_SERVICE_NAME)
            .build();
    private static final String MANAGEMENT_SCOPE = "MANAGEMENT_API";
    @Inject
    private WebService webService;
    @Inject
    private WebServer webServer;
    @Inject
    private AuthenticationService authenticationService;
    @Inject
    private WebServiceConfigurer configurator;
    @Inject
    private TypeManager typeManager;
    @Inject
    private JsonLd jsonLd;
    @Inject
    private TypeTransformerRegistry transformerRegistry;
    @Inject
    private ParticipantIdMapper participantIdMapper;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var webServiceConfiguration = configurator.configure(context, webServer, SETTINGS);

        context.registerService(ManagementApiConfiguration.class, new ManagementApiConfiguration(webServiceConfiguration));
        webService.registerResource(webServiceConfiguration.getContextAlias(), new AuthenticationRequestFilter(authenticationService));

        jsonLd.registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, MANAGEMENT_SCOPE);
        var jsonLdMapper = typeManager.getMapper(JSON_LD);
        webService.registerResource(webServiceConfiguration.getContextAlias(), new ObjectMapperProvider(jsonLdMapper));
        webService.registerResource(webServiceConfiguration.getContextAlias(), new JerseyJsonLdInterceptor(jsonLd, jsonLdMapper, MANAGEMENT_SCOPE));

        var managementApiTransformerRegistry = transformerRegistry.forContext("management-api");

        var factory = Json.createBuilderFactory(Map.of());
        managementApiTransformerRegistry.register(new JsonObjectFromContractAgreementTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromDataAddressTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromAssetTransformer(factory, jsonLdMapper));
        managementApiTransformerRegistry.register(new JsonObjectFromPolicyTransformer(factory, participantIdMapper));
        managementApiTransformerRegistry.register(new JsonObjectFromQuerySpecTransformer(factory));
        managementApiTransformerRegistry.register(new JsonObjectFromCriterionTransformer(factory, jsonLdMapper));

        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper).forEach(managementApiTransformerRegistry::register);
        managementApiTransformerRegistry.register(new JsonObjectToDataAddressTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToCriterionTransformer());
        managementApiTransformerRegistry.register(new JsonObjectToAssetTransformer());
        managementApiTransformerRegistry.register(new JsonValueToGenericTypeTransformer(jsonLdMapper));
    }
}
