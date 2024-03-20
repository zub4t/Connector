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

package org.eclipse.edc.iam.identitytrust.core;

import jakarta.json.Json;
import org.eclipse.edc.iam.did.spi.resolution.DidPublicKeyResolver;
import org.eclipse.edc.iam.did.spi.resolution.DidResolverRegistry;
import org.eclipse.edc.iam.identitytrust.DidCredentialServiceUrlResolver;
import org.eclipse.edc.iam.identitytrust.IdentityAndTrustService;
import org.eclipse.edc.iam.identitytrust.core.defaults.DefaultCredentialServiceClient;
import org.eclipse.edc.iam.identitytrust.verification.MultiFormatPresentationVerifier;
import org.eclipse.edc.identitytrust.ClaimTokenCreatorFunction;
import org.eclipse.edc.identitytrust.CredentialServiceClient;
import org.eclipse.edc.identitytrust.IatpParticipantAgentServiceExtension;
import org.eclipse.edc.identitytrust.SecureTokenService;
import org.eclipse.edc.identitytrust.TrustedIssuerRegistry;
import org.eclipse.edc.identitytrust.validation.TokenValidationAction;
import org.eclipse.edc.identitytrust.verification.PresentationVerifier;
import org.eclipse.edc.identitytrust.verification.SignatureSuiteRegistry;
import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.security.signature.jws2020.JwsSignature2020Suite;
import org.eclipse.edc.spi.agent.ParticipantAgentService;
import org.eclipse.edc.spi.http.EdcHttpClient;
import org.eclipse.edc.spi.iam.IdentityService;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.types.TypeManager;
import org.eclipse.edc.token.rules.AudienceValidationRule;
import org.eclipse.edc.token.rules.ExpirationIssuedAtValidationRule;
import org.eclipse.edc.token.spi.TokenValidationRulesRegistry;
import org.eclipse.edc.token.spi.TokenValidationService;
import org.eclipse.edc.transform.spi.TypeTransformerRegistry;
import org.eclipse.edc.verifiablecredentials.jwt.JwtPresentationVerifier;
import org.eclipse.edc.verifiablecredentials.jwt.rules.HasSubjectRule;
import org.eclipse.edc.verifiablecredentials.jwt.rules.IssuerEqualsSubjectRule;
import org.eclipse.edc.verifiablecredentials.jwt.rules.JtiValidationRule;
import org.eclipse.edc.verifiablecredentials.jwt.rules.SubJwkIsNullRule;
import org.eclipse.edc.verifiablecredentials.jwt.rules.TokenNotNullRule;
import org.eclipse.edc.verifiablecredentials.linkeddata.DidMethodResolver;
import org.eclipse.edc.verifiablecredentials.linkeddata.LdpVerifier;
import org.jetbrains.annotations.NotNull;

import java.time.Clock;
import java.util.Map;

import static org.eclipse.edc.spi.CoreConstants.JSON_LD;
import static org.eclipse.edc.verifiablecredentials.jwt.JwtPresentationVerifier.JWT_VC_TOKEN_CONTEXT;

@Extension("Identity And Trust Extension")
public class IdentityAndTrustExtension implements ServiceExtension {

    @Setting(value = "DID of this connector", required = true)
    public static final String CONNECTOR_DID_PROPERTY = "edc.iam.issuer.id";
    public static final String IATP_SELF_ISSUED_TOKEN_CONTEXT = "iatp-si";

    public static final String JSON_2020_SIGNATURE_SUITE = "JsonWebSignature2020";


    @Inject
    private SecureTokenService secureTokenService;

    @Inject
    private TrustedIssuerRegistry registry;

    @Inject
    private TypeManager typeManager;

    @Inject
    private SignatureSuiteRegistry signatureSuiteRegistry;

    @Inject
    private JsonLd jsonLd;

    @Inject
    private Clock clock;

    @Inject
    private EdcHttpClient httpClient;

    @Inject
    private TypeTransformerRegistry typeTransformerRegistry;

    @Inject
    private DidResolverRegistry didResolverRegistry;

    @Inject
    private TokenValidationService tokenValidationService;

    @Inject
    private TokenValidationRulesRegistry rulesRegistry;
    @Inject
    private DidPublicKeyResolver didPublicKeyResolver;
    @Inject
    private ClaimTokenCreatorFunction claimTokenFunction;

    @Inject
    private ParticipantAgentService participantAgentService;

    @Inject
    private IatpParticipantAgentServiceExtension participantAgentServiceExtension;

    private PresentationVerifier presentationVerifier;
    private CredentialServiceClient credentialServiceClient;

    @Override
    public void initialize(ServiceExtensionContext context) {

        // add all rules for self-issued ID tokens
        rulesRegistry.addRule(IATP_SELF_ISSUED_TOKEN_CONTEXT, new IssuerEqualsSubjectRule());
        rulesRegistry.addRule(IATP_SELF_ISSUED_TOKEN_CONTEXT, new SubJwkIsNullRule());
        rulesRegistry.addRule(IATP_SELF_ISSUED_TOKEN_CONTEXT, new AudienceValidationRule(getOwnDid(context)));
        rulesRegistry.addRule(IATP_SELF_ISSUED_TOKEN_CONTEXT, new JtiValidationRule(context.getMonitor()));
        rulesRegistry.addRule(IATP_SELF_ISSUED_TOKEN_CONTEXT, new ExpirationIssuedAtValidationRule(clock, 5));
        rulesRegistry.addRule(IATP_SELF_ISSUED_TOKEN_CONTEXT, new TokenNotNullRule());

        // add all rules for validating VerifiableCredential JWTs
        rulesRegistry.addRule(JWT_VC_TOKEN_CONTEXT, new HasSubjectRule());

        // TODO move in a separated extension?
        signatureSuiteRegistry.register(JSON_2020_SIGNATURE_SUITE, new JwsSignature2020Suite(typeManager.getMapper(JSON_LD)));

        participantAgentService.register(participantAgentServiceExtension);
    }

    @Provider
    public IdentityService createIdentityService(ServiceExtensionContext context) {

        var credentialServiceUrlResolver = new DidCredentialServiceUrlResolver(didResolverRegistry);

        var validationAction = tokenValidationAction();

        return new IdentityAndTrustService(secureTokenService, getOwnDid(context), getPresentationVerifier(context),
                getCredentialServiceClient(context), validationAction, registry, clock, credentialServiceUrlResolver, claimTokenFunction);
    }

    @Provider
    public CredentialServiceClient getCredentialServiceClient(ServiceExtensionContext context) {
        if (credentialServiceClient == null) {
            credentialServiceClient = new DefaultCredentialServiceClient(httpClient, Json.createBuilderFactory(Map.of()),
                    typeManager.getMapper(JSON_LD), typeTransformerRegistry, jsonLd, context.getMonitor());
        }
        return credentialServiceClient;
    }

    @Provider
    public PresentationVerifier getPresentationVerifier(ServiceExtensionContext context) {
        if (presentationVerifier == null) {
            var mapper = typeManager.getMapper(JSON_LD);

            var jwtVerifier = new JwtPresentationVerifier(mapper, tokenValidationService, rulesRegistry, didPublicKeyResolver);
            var ldpVerifier = LdpVerifier.Builder.newInstance()
                    .signatureSuites(signatureSuiteRegistry)
                    .jsonLd(jsonLd)
                    .objectMapper(mapper)
                    .methodResolver(new DidMethodResolver(didResolverRegistry))
                    .build();

            presentationVerifier = new MultiFormatPresentationVerifier(getOwnDid(context), jwtVerifier, ldpVerifier);
        }
        return presentationVerifier;
    }

    @NotNull
    private TokenValidationAction tokenValidationAction() {
        return (tokenRepresentation) -> {
            var rules = rulesRegistry.getRules(IATP_SELF_ISSUED_TOKEN_CONTEXT);
            return tokenValidationService.validate(tokenRepresentation, didPublicKeyResolver, rules);
        };
    }

    private String getOwnDid(ServiceExtensionContext context) {
        return context.getConfig().getString(CONNECTOR_DID_PROPERTY);
    }

}
