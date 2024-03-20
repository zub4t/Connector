/*
 *  Copyright (c) 2023 Fraunhofer Institute for Software and Systems Engineering
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Fraunhofer Institute for Software and Systems Engineering - initial API and implementation
 *
 */

package org.eclipse.edc.protocol.dsp.api.configuration;

import jakarta.json.Json;
import org.eclipse.edc.core.transform.transformer.dspace.from.JsonObjectFromDataAddressDspaceTransformer;
import org.eclipse.edc.core.transform.transformer.dspace.to.JsonObjectToDataAddressDspaceTransformer;
import org.eclipse.edc.core.transform.transformer.edc.from.JsonObjectFromAssetTransformer;
import org.eclipse.edc.core.transform.transformer.edc.from.JsonObjectFromCriterionTransformer;
import org.eclipse.edc.core.transform.transformer.edc.from.JsonObjectFromQuerySpecTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonObjectToAssetTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonObjectToCriterionTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonObjectToQuerySpecTransformer;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.core.transform.transformer.odrl.OdrlTransformersFactory;
import org.eclipse.edc.core.transform.transformer.odrl.from.JsonObjectFromPolicyTransformer;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.protocol.dsp.spi.configuration.DspApiConfiguration;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provides;
import org.eclipse.edc.spi.agent.ParticipantIdMapper;
import org.eclipse.edc.spi.protocol.ProtocolWebhook;
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

import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCAT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DCT_SCHEMA;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_PREFIX;
import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_PREFIX;
import static org.eclipse.edc.policy.model.OdrlNamespace.ODRL_SCHEMA;
import static org.eclipse.edc.protocol.dsp.type.DspConstants.DSP_SCOPE;
import static org.eclipse.edc.spi.CoreConstants.JSON_LD;

/**
 * Provides the configuration for the Dataspace Protocol API context. Creates the API context
 * using {@link #DEFAULT_PROTOCOL_PORT} and {@link #DEFAULT_PROTOCOL_API_PATH}, if no respective
 * settings are provided. Configures the API context to allow Jakarta JSON-API types as endpoint
 * parameters.
 */
@Extension(value = DspApiConfigurationExtension.NAME)
@Provides({ DspApiConfiguration.class, ProtocolWebhook.class })
public class DspApiConfigurationExtension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol API Configuration Extension";

    public static final String CONTEXT_ALIAS = "protocol";

    public static final String DEFAULT_DSP_CALLBACK_ADDRESS = "http://localhost:8282/api/v1/dsp";
    public static final String DSP_CALLBACK_ADDRESS = "edc.dsp.callback.address";

    public static final int DEFAULT_PROTOCOL_PORT = 8282;
    public static final String DEFAULT_PROTOCOL_API_PATH = "/api/v1/dsp";

    public static final WebServiceSettings SETTINGS = WebServiceSettings.Builder.newInstance()
            .apiConfigKey("web.http.protocol")
            .contextAlias(CONTEXT_ALIAS)
            .defaultPath(DEFAULT_PROTOCOL_API_PATH)
            .defaultPort(DEFAULT_PROTOCOL_PORT)
            .name("Protocol API")
            .build();
    @Inject
    private TypeManager typeManager;
    @Inject
    private WebService webService;
    @Inject
    private WebServer webServer;
    @Inject
    private WebServiceConfigurer configurator;
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
        var config = configurator.configure(context, webServer, SETTINGS);
        var dspWebhookAddress = context.getSetting(DSP_CALLBACK_ADDRESS, DEFAULT_DSP_CALLBACK_ADDRESS);
        context.registerService(DspApiConfiguration.class, new DspApiConfiguration(config.getContextAlias(), dspWebhookAddress));
        context.registerService(ProtocolWebhook.class, () -> dspWebhookAddress);

        var jsonLdMapper = typeManager.getMapper(JSON_LD);


        // registers ns for DSP scope
        jsonLd.registerNamespace(DCAT_PREFIX, DCAT_SCHEMA, DSP_SCOPE);
        jsonLd.registerNamespace(DCT_PREFIX, DCT_SCHEMA, DSP_SCOPE);
        jsonLd.registerNamespace(ODRL_PREFIX, ODRL_SCHEMA, DSP_SCOPE);
        jsonLd.registerNamespace(DSPACE_PREFIX, DSPACE_SCHEMA, DSP_SCOPE);

        webService.registerResource(config.getContextAlias(), new ObjectMapperProvider(jsonLdMapper));
        webService.registerResource(config.getContextAlias(), new JerseyJsonLdInterceptor(jsonLd, jsonLdMapper, DSP_SCOPE));

        registerTransformers();
    }

    private void registerTransformers() {
        var mapper = typeManager.getMapper(JSON_LD);
        mapper.registerSubtypes(AtomicConstraint.class, LiteralExpression.class);

        var jsonBuilderFactory = Json.createBuilderFactory(Map.of());

        // EDC model to JSON-LD transformers
        var dspApiTransformerRegistry = transformerRegistry.forContext("dsp-api");
        dspApiTransformerRegistry.register(new JsonObjectFromPolicyTransformer(jsonBuilderFactory, participantIdMapper));
        dspApiTransformerRegistry.register(new JsonObjectFromAssetTransformer(jsonBuilderFactory, mapper));
        dspApiTransformerRegistry.register(new JsonObjectFromDataAddressDspaceTransformer(jsonBuilderFactory, mapper));
        dspApiTransformerRegistry.register(new JsonObjectFromQuerySpecTransformer(jsonBuilderFactory));
        dspApiTransformerRegistry.register(new JsonObjectFromCriterionTransformer(jsonBuilderFactory, mapper));

        // JSON-LD to EDC model transformers
        // ODRL Transformers
        OdrlTransformersFactory.jsonObjectToOdrlTransformers(participantIdMapper).forEach(dspApiTransformerRegistry::register);

        dspApiTransformerRegistry.register(new JsonValueToGenericTypeTransformer(mapper));
        dspApiTransformerRegistry.register(new JsonObjectToAssetTransformer());
        dspApiTransformerRegistry.register(new JsonObjectToQuerySpecTransformer());
        dspApiTransformerRegistry.register(new JsonObjectToCriterionTransformer());
        dspApiTransformerRegistry.register(new JsonObjectToDataAddressDspaceTransformer());
    }

}
