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

package org.eclipse.edc.core.transform.transformer.edc.from;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.JsonBuilderFactory;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.TYPE;
import static org.eclipse.edc.jsonld.spi.JsonLdKeywords.VALUE;

public class JsonObjectFromCriterionTransformer extends AbstractJsonLdTransformer<Criterion, JsonObject> {
    private final JsonBuilderFactory jsonFactory;
    private final ObjectMapper mapper;

    public JsonObjectFromCriterionTransformer(JsonBuilderFactory jsonFactory, ObjectMapper mapper) {
        super(Criterion.class, JsonObject.class);
        this.jsonFactory = jsonFactory;
        this.mapper = mapper;
    }

    @Override
    public @Nullable JsonObject transform(@NotNull Criterion querySpec, @NotNull TransformerContext context) {
        var builder = jsonFactory.createObjectBuilder();
        builder.add(TYPE, Criterion.CRITERION_TYPE);

        addValue(builder, Criterion.CRITERION_OPERAND_LEFT, querySpec.getOperandLeft(), context);

        builder.add(Criterion.CRITERION_OPERATOR, querySpec.getOperator());

        if (querySpec.getOperandRight() != null) {
            addValue(builder, Criterion.CRITERION_OPERAND_RIGHT, querySpec.getOperandRight(), context);
        }

        return builder.build();
    }

    private void addValue(JsonObjectBuilder builder, String field, Object value, TransformerContext context) {
        try {
            builder.add(field, mapper.convertValue(value, JsonValue.class));
        } catch (IllegalArgumentException e) {
            context.problem()
                    .invalidProperty()
                    .type(VALUE)
                    .property(field)
                    .value(value != null ? value.toString() : "null")
                    .error(e.getMessage())
                    .report();
        }
    }
}
