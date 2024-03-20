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

package org.eclipse.edc.protocol.dsp.catalog.transform;

import jakarta.json.Json;
import org.eclipse.edc.core.transform.transformer.dcat.from.JsonObjectFromCatalogTransformer;
import org.eclipse.edc.core.transform.transformer.dcat.from.JsonObjectFromDataServiceTransformer;
import org.eclipse.edc.core.transform.transformer.dcat.from.JsonObjectFromDatasetTransformer;
import org.eclipse.edc.core.transform.transformer.dcat.from.JsonObjectFromDistributionTransformer;
import org.eclipse.edc.protocol.dsp.catalog.transform.from.JsonObjectFromCatalogRequestMessageTransformer;
import org.eclipse.edc.protocol.dsp.catalog.transform.to.JsonObjectToCatalogRequestMessageTransformer;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.agent.ParticipantIdMapper;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;

import java.util.Map;

import static org.eclipse.edc.spi.CoreConstants.JSON_LD;

/**
 * Provides the transformers for catalog message types via the {@link TypeTransformerRegistry}.
 */
@Extension(value = DspCatalogTransformExtension.NAME)
public class DspCatalogTransformExtension implements ServiceExtension {

    public static final String NAME = "Dataspace Protocol Catalog Transform Extension";

    @Inject
    private TypeTransformerRegistry registry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private ParticipantIdMapper participantIdMapper;

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var jsonFactory = Json.createBuilderFactory(Map.of());
        var mapper = typeManager.getMapper(JSON_LD);

        var dspApiTransformerRegistry = registry.forContext("dsp-api");
        dspApiTransformerRegistry.register(new JsonObjectFromCatalogRequestMessageTransformer(jsonFactory));
        dspApiTransformerRegistry.register(new JsonObjectToCatalogRequestMessageTransformer());

        dspApiTransformerRegistry.register(new JsonObjectFromCatalogTransformer(jsonFactory, mapper, participantIdMapper));
        dspApiTransformerRegistry.register(new JsonObjectFromDatasetTransformer(jsonFactory, mapper));
        dspApiTransformerRegistry.register(new JsonObjectFromDistributionTransformer(jsonFactory));
        dspApiTransformerRegistry.register(new JsonObjectFromDataServiceTransformer(jsonFactory));
    }
}
