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

package org.eclipse.edc.connector.transfer.spi.types;

import org.eclipse.edc.connector.transfer.spi.flow.DataFlowManager;
import org.eclipse.edc.spi.types.domain.DataAddress;

/**
 * A Response for {@link DataFlowManager#start} operation
 */
public class DataFlowResponse {

    private DataAddress dataAddress;
    private String dataPlaneId;

    private DataFlowResponse() {
    }

    public DataAddress getDataAddress() {
        return dataAddress;
    }

    public String getDataPlaneId() {
        return dataPlaneId;
    }

    public static class Builder {

        DataFlowResponse response;

        private Builder() {
            response = new DataFlowResponse();
        }

        public static Builder newInstance() {
            return new Builder();
        }

        public Builder dataAddress(DataAddress dataAddress) {
            response.dataAddress = dataAddress;
            return this;
        }

        public Builder dataPlaneId(String dataPlaneId) {
            response.dataPlaneId = dataPlaneId;
            return this;
        }

        public DataFlowResponse build() {
            return response;
        }
    }
}
