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

package org.eclipse.edc.protocol.dsp.version;

import org.eclipse.edc.connector.spi.protocol.ProtocolVersion;

public interface DspVersions {

    String V_2024_1_VERSION = "2024/1";
    String V_2024_1_PATH = "/" + V_2024_1_VERSION;
    ProtocolVersion V_2024_1 = new ProtocolVersion(V_2024_1_VERSION, V_2024_1_PATH);

}
