/*
 *  Copyright (c) 2022 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 *       ZF Friedrichshafen AG - added private property support
 *
 */

package org.eclipse.edc.spi.result;

import java.util.List;

public class StoreFailure extends Failure {
    private final Reason reason;

    public StoreFailure(List<String> messages, Reason reason) {
        super(messages);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        NOT_FOUND, ALREADY_EXISTS, DUPLICATE_KEYS, ALREADY_LEASED, GENERAL_ERROR
    }
}
