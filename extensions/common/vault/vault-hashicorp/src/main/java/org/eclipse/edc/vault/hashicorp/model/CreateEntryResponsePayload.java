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

package org.eclipse.edc.vault.hashicorp.model;

import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

public class CreateEntryResponsePayload {

    private EntryMetadata data;

    private CreateEntryResponsePayload() {
    }

    public EntryMetadata getData() {
        return this.data;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final CreateEntryResponsePayload createEntryResponsePayload;

        private Builder() {
            createEntryResponsePayload = new CreateEntryResponsePayload();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder data(EntryMetadata data) {
            createEntryResponsePayload.data = data;
            return this;
        }

        public CreateEntryResponsePayload build() {
            return createEntryResponsePayload;
        }
    }
}
