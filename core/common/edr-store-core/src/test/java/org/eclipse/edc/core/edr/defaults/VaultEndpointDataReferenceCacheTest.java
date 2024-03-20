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

package org.eclipse.edc.core.edr.defaults;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.core.vault.InMemoryVault;
import org.eclipse.edc.edr.spi.store.EndpointDataReferenceCache;
import org.eclipse.tractusx.edc.edr.spi.store.EndpointDataReferenceCacheTestBase;

import static org.mockito.Mockito.mock;

public class VaultEndpointDataReferenceCacheTest extends EndpointDataReferenceCacheTestBase {

    private final VaultEndpointDataReferenceCache cache = new VaultEndpointDataReferenceCache(new InMemoryVault(mock()), "", new ObjectMapper());

    @Override
    protected EndpointDataReferenceCache getCache() {
        return cache;
    }
}
