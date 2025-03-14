package com.example.demo.security;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;

import java.net.URL;
import java.security.interfaces.RSAPublicKey;

public class GoogleTokenVerifier {

    private static final String GOOGLE_JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String EXPECTED_ISSUER = "https://accounts.google.com";
    // Replace with your actual client ID from Google Cloud
    private static final String CLIENT_ID = "954183420749-55u8nr8817bcae6ogpogq5etek3q031b.apps.googleusercontent.com";

    public static void verifyToken(String authorizationHeader) throws Exception {
        // 1. Check that we have a "Bearer " header
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new JWTVerificationException("Missing or invalid Authorization header");
        }
        // 2. Extract the JWT token
        String token = authorizationHeader.substring("Bearer ".length());

        // 3. Download Google's public keys
        JwkProvider provider = new UrlJwkProvider(new URL(GOOGLE_JWKS_URL));

        // 4. Decode the token to see which key ID we need
        DecodedJWT decoded = JWT.decode(token);
        String keyId = decoded.getKeyId();
        if (keyId == null) {
            throw new JWTVerificationException("Token missing key ID (kid)");
        }

        // 5. Fetch the matching key from Google
        Jwk jwk = provider.get(keyId);
        // 6. Build an RSA256 Algorithm from the public key
        Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) jwk.getPublicKey(), null);

        // 7. Create verifier with expected issuer + audience
        JWTVerifier verifier = JWT.require(algorithm)
            .withIssuer(EXPECTED_ISSUER)
            .withAudience(CLIENT_ID)
            .build();

        // 8. Verify
        verifier.verify(token); // throws if invalid
    }
}
