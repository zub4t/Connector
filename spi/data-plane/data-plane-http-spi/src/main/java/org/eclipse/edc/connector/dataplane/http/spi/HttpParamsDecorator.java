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

package org.eclipse.edc.connector.dataplane.http.spi;

import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;

/**
 * Define how to decorate the {@link HttpRequestParams} builder.
 */
@FunctionalInterface
public interface HttpParamsDecorator {

    /**
     * Decorate params with information coming from the request and the data address. Return the param object.
     */
    HttpRequestParams.Builder decorate(DataFlowStartMessage request, HttpDataAddress address, HttpRequestParams.Builder params);
}
