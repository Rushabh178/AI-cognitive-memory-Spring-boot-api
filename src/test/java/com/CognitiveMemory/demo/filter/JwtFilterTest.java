package com.CognitiveMemory.demo.filter;

import com.CognitiveMemory.demo.utils.JwtUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression: an expired/invalid token made extractUsername throw inside the filter; the
 * exception escaped, the container forwarded to /error (unauthenticated) and the client got a
 * 403, which the frontend's refresh-on-401 interceptor ignores ("Failed to load conversation").
 */
class JwtFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-test-secret-0123456789";
    private static final String OTHER_SECRET = "another-secret-another-secret-another-secret-0123456789";

    private final JwtUtil jwtUtil = jwtUtil(SECRET, 60_000);
    private final UserDetailsService users = username -> {
        if (!"alice".equals(username)) {
            throw new UsernameNotFoundException("no such user: " + username);
        }
        return User.withUsername("alice").password("x").roles("USER").build();
    };
    private final JwtFilter filter = new JwtFilter(jwtUtil, users);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private static JwtUtil jwtUtil(String secret, long expirationMs) {
        JwtUtil util = new JwtUtil();
        ReflectionTestUtils.setField(util, "secretKey", secret);
        ReflectionTestUtils.setField(util, "jwtExpiration", expirationMs);
        ReflectionTestUtils.setField(util, "refreshExpiration", 600_000L);
        return util;
    }

    private MockHttpServletResponse run(String path, String authorization, MockFilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private void assertRejected(MockHttpServletResponse response, MockFilterChain chain, String reason) throws Exception {
        assertEquals(401, response.getStatus());
        assertNull(chain.getRequest(), "request must not continue down the chain");
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("{\"error\":\"Token expired or invalid\",\"reason\":\"" + reason + "\"}",
                response.getContentAsString());
        assertEquals("Bearer error=\"invalid_token\"", response.getHeader("WWW-Authenticate"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void expiredTokenGetsClean401() throws Exception {
        String expired = jwtUtil(SECRET, -1_000).generateToken("alice");
        MockFilterChain chain = new MockFilterChain();
        assertRejected(run("/sessions/list", "Bearer " + expired, chain), chain, "expired");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not.a.jwt", "garbage", "eyJhbGciOiJIUzI1NiJ9.e30.bad-signature", ""})
    void malformedOrEmptyTokenGetsClean401(String token) throws Exception {
        MockFilterChain chain = new MockFilterChain();
        assertRejected(run("/sessions/list", "Bearer " + token, chain), chain, "invalid");
    }

    @Test
    void tokenSignedWithAnotherKeyGetsClean401() throws Exception {
        String forged = jwtUtil(OTHER_SECRET, 60_000).generateToken("alice");
        MockFilterChain chain = new MockFilterChain();
        assertRejected(run("/sessions/list", "Bearer " + forged, chain), chain, "invalid");
    }

    @Test
    void validTokenForDeletedUserGetsClean401() throws Exception {
        String token = jwtUtil.generateToken("bob"); // signed correctly, but bob doesn't exist
        MockFilterChain chain = new MockFilterChain();
        assertRejected(run("/sessions/list", "Bearer " + token, chain), chain, "invalid");
    }

    @Test
    void validTokenAuthenticatesAndContinues() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/sessions/list", "Bearer " + jwtUtil.generateToken("alice"), chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
        assertEquals("alice", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @Test
    void noAuthorizationHeaderContinuesUnauthenticated() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        run("/sessions/list", null, chain);
        assertNotNull(chain.getRequest(), "left to Spring Security's authorization rules");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void publicEndpointIgnoresStaleToken() throws Exception {
        String expired = jwtUtil(SECRET, -1_000).generateToken("alice");
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run("/public/login", "Bearer " + expired, chain);
        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest(), "public endpoints must never be blocked by a stale token");
    }
}
