package com.patchsentry.ledger.web;

import com.patchsentry.ledger.core.RunnerTokens;
import com.patchsentry.ledger.persistence.RunnerTokenEntity;
import com.patchsentry.ledger.persistence.RunnerTokenRepository;
import com.patchsentry.ledger.ratelimit.RateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates runners on {@code /api/runner/*}: syntax check, hash lookup,
 * then a per-token rate limit. On success the token's project ID is attached to
 * the request, so controllers never trust a project ID sent by the client.
 */
public class RunnerAuthFilter extends OncePerRequestFilter {

    public static final String PROJECT_ATTRIBUTE = "ledger.projectId";

    private final RunnerTokenRepository tokens;
    private final RateLimiter rateLimiter;
    private final ProblemResponses problems;

    public RunnerAuthFilter(RunnerTokenRepository tokens, RateLimiter rateLimiter, ProblemResponses problems) {
        this.tokens = tokens;
        this.rateLimiter = rateLimiter;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = BearerTokens.from(request);
        if (!RunnerTokens.looksValid(token)) {
            problems.write(response, HttpStatus.UNAUTHORIZED, "Missing or malformed runner token");
            return;
        }
        Optional<RunnerTokenEntity> match = tokens.findByTokenHashAndRevokedAtIsNull(RunnerTokens.hash(token));
        if (match.isEmpty()) {
            problems.write(response, HttpStatus.UNAUTHORIZED, "Unknown or revoked runner token");
            return;
        }
        RunnerTokenEntity runner = match.get();
        if (!rateLimiter.tryAcquire(runner.getId().toString())) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "60");
            problems.write(response, HttpStatus.TOO_MANY_REQUESTS, "Rate limit exceeded for this runner token");
            return;
        }
        request.setAttribute(PROJECT_ATTRIBUTE, runner.getProjectId());
        chain.doFilter(request, response);
    }
}
