package com.patchsentry.ledger.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patchsentry.ledger.core.RunnerTokens;
import com.patchsentry.ledger.persistence.RunnerTokenEntity;
import com.patchsentry.ledger.persistence.RunnerTokenRepository;
import com.patchsentry.ledger.ratelimit.RateLimiter;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class RunnerAuthFilterTest {

    @Mock
    private RunnerTokenRepository tokens;

    @Mock
    private RateLimiter rateLimiter;

    private RunnerAuthFilter filter;
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final MockFilterChain chain = new MockFilterChain();

    @BeforeEach
    void setUp() {
        filter = new RunnerAuthFilter(tokens, rateLimiter, new ProblemResponses(new ObjectMapper()));
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/runner/runs/abc");
        if (authorization != null) {
            request.addHeader("Authorization", authorization);
        }
        return request;
    }

    @Test
    void malformedTokensAreRejectedWithoutADatabaseLookup() throws Exception {
        filter.doFilter(request("Bearer not-a-token"), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
        verifyNoInteractions(tokens, rateLimiter);
    }

    @Test
    void unknownTokensAreRejected() throws Exception {
        String token = RunnerTokens.issue().plaintext();
        when(tokens.findByTokenHashAndRevokedAtIsNull(RunnerTokens.hash(token))).thenReturn(Optional.empty());

        filter.doFilter(request("Bearer " + token), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void validTokensAttachTheTokensProjectAndContinue() throws Exception {
        RunnerTokens.IssuedToken issued = RunnerTokens.issue();
        UUID projectId = UUID.randomUUID();
        RunnerTokenEntity entity = new RunnerTokenEntity(UUID.randomUUID(), projectId, issued.hash(),
                issued.displayPrefix(), Instant.now());
        when(tokens.findByTokenHashAndRevokedAtIsNull(issued.hash())).thenReturn(Optional.of(entity));
        when(rateLimiter.tryAcquire(anyString())).thenReturn(true);
        MockHttpServletRequest request = request("Bearer " + issued.plaintext());

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(request.getAttribute(RunnerAuthFilter.PROJECT_ATTRIBUTE)).isEqualTo(projectId);
    }

    @Test
    void rateLimitedTokensGet429WithRetryAfter() throws Exception {
        RunnerTokens.IssuedToken issued = RunnerTokens.issue();
        RunnerTokenEntity entity = new RunnerTokenEntity(UUID.randomUUID(), UUID.randomUUID(), issued.hash(),
                issued.displayPrefix(), Instant.now());
        when(tokens.findByTokenHashAndRevokedAtIsNull(issued.hash())).thenReturn(Optional.of(entity));
        when(rateLimiter.tryAcquire(anyString())).thenReturn(false);

        filter.doFilter(request("Bearer " + issued.plaintext()), response, chain);

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(chain.getRequest()).isNull();
    }
}
