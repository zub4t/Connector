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

package org.eclipse.edc.iam.identitytrust.transform.serde;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonObject;
import org.eclipse.edc.core.transform.TransformerContextImpl;
import org.eclipse.edc.core.transform.TypeTransformerRegistryImpl;
import org.eclipse.edc.core.transform.transformer.edc.to.JsonValueToGenericTypeTransformer;
import org.eclipse.edc.iam.identitytrust.transform.from.JsonObjectFromPresentationResponseMessageTransformer;
import org.eclipse.edc.iam.identitytrust.transform.to.JsonObjectToPresentationResponseMessageTransformer;
import org.eclipse.edc.jsonld.TitaniumJsonLd;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.jsonld.util.JacksonJsonLd;
import org.eclipse.edc.junit.testfixtures.TestUtils;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.edc.identitytrust.VcConstants.IATP_CONTEXT_URL;
import static org.mockito.Mockito.mock;

public class PresentationResponseMessageSerdeTest {

    private final JsonLd jsonLd = new TitaniumJsonLd(mock());

    private final ObjectMapper mapper = JacksonJsonLd.createObjectMapper();

    private final TypeTransformerRegistry trr = new TypeTransformerRegistryImpl();
    private final TransformerContext context = new TransformerContextImpl(trr);
    private final JsonObjectFromPresentationResponseMessageTransformer fromTransformer = new JsonObjectFromPresentationResponseMessageTransformer();
    private final JsonObjectToPresentationResponseMessageTransformer toTransformer = new JsonObjectToPresentationResponseMessageTransformer(mapper);

    @BeforeEach
    void setUp() {
        jsonLd.registerCachedDocument("https://identity.foundation/presentation-exchange/submission/v1", TestUtils.getFileFromResourceName("presentation_ex.json").toURI());
        jsonLd.registerCachedDocument(IATP_CONTEXT_URL, TestUtils.getFileFromResourceName("document/iatp.v08.jsonld").toURI());
        jsonLd.registerContext(IATP_CONTEXT_URL);
        // delegate to the generic transformer

        trr.register(new JsonValueToGenericTypeTransformer(mapper));
    }


    @Test
    void serde() throws JsonProcessingException {

        var obj = """
                {
                         "@context": [
                             "https://w3id.org/tractusx-trust/v0.8"
                         ],
                         "@type": "PresentationResponseMessage",
                         "presentation": [
                             {
                                 "@context": [
                                     "https://www.w3.org/2018/credentials/v1"
                                 ],
                                 "type": [
                                     "VerifiablePresentation"
                                 ]
                             },
                             "jwtPresentation"
                         ]
                     }
                """;

        var json = mapper.readValue(obj, JsonObject.class);
        var jo = jsonLd.expand(json);

        var query = toTransformer.transform(jo.getContent(), context);
        assertThat(query).isNotNull();

        var expandedJson = fromTransformer.transform(query, context);

        var compacted = jsonLd.compact(expandedJson).getContent();

        assertThat(json.getJsonArray("@context")).isEqualTo(compacted.getJsonArray("@context"));
        assertThat(json.getJsonArray("presentation")).isEqualTo(compacted.getJsonArray("presentation"));
        assertThat(json.getJsonString("@type")).isEqualTo(compacted.getJsonString("type"));
    }
}
