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

package org.eclipse.edc.protocol.dsp.type;

import static org.eclipse.edc.jsonld.spi.Namespaces.DSPACE_SCHEMA;

/**
 * This class provides generic dsp property and type names.
 */
public interface DspPropertyAndTypeNames {

    String DSPACE_PROPERTY_CODE = DSPACE_SCHEMA + "code";
    String DSPACE_PROPERTY_REASON = DSPACE_SCHEMA + "reason";
    String DSPACE_PROPERTY_CONSUMER_PID = DSPACE_SCHEMA + "consumerPid";
    String DSPACE_PROPERTY_PROVIDER_PID = DSPACE_SCHEMA + "providerPid";
    @Deprecated(since = "0.5.1")
    String DSPACE_PROPERTY_PROCESS_ID = DSPACE_SCHEMA + "processId";
    String DSPACE_PROPERTY_CALLBACK_ADDRESS = DSPACE_SCHEMA + "callbackAddress";
    String DSPACE_PROPERTY_STATE = DSPACE_SCHEMA + "state";
}
