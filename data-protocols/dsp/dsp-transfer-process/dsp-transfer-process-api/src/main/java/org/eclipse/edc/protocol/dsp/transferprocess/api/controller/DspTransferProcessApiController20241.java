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

package org.eclipse.edc.protocol.dsp.transferprocess.api.controller;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.edc.connector.spi.transferprocess.TransferProcessProtocolService;
import org.eclipse.edc.protocol.dsp.spi.message.DspRequestHandler;
import org.eclipse.edc.protocol.dsp.version.DspVersions;

import static org.eclipse.edc.protocol.dsp.transferprocess.api.TransferProcessApiPaths.BASE_PATH;


/**
 * Versioned Transfer endpoint, same as {@link DspTransferProcessApiController} but exposed on the /2024/1 path
 */
@Consumes({ MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_JSON })
@Path(DspVersions.V_2024_1_PATH + BASE_PATH)
public class DspTransferProcessApiController20241 extends DspTransferProcessApiController {

    public DspTransferProcessApiController20241(TransferProcessProtocolService protocolService, DspRequestHandler dspRequestHandler) {
        super(protocolService, dspRequestHandler);
    }
}
