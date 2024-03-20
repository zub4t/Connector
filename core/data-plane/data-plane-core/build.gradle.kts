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
 *
 */

plugins {
    `java-library`
}

dependencies {
    api(project(":spi:common:core-spi"))
    api(project(":spi:common:web-spi"))
    api(project(":spi:control-plane:control-plane-api-client-spi"))
    api(project(":spi:data-plane:data-plane-spi"))

    implementation(project(":spi:common:token-spi"))
    implementation(project(":core:common:token-core")) // for the JwtGenerationService
    implementation(project(":core:common:connector-core"))
    implementation(project(":core:common:boot"))
    implementation(project(":core:common:state-machine"))
    implementation(project(":core:common:util"))
    implementation(project(":core:data-plane:data-plane-util"))
    implementation(project(":extensions:common:http"))

    implementation(libs.opentelemetry.instrumentation.annotations)

    testImplementation(project(":core:common:junit"))
    testImplementation(libs.awaitility)
    testImplementation(testFixtures(project(":spi:data-plane:data-plane-spi")))
}


