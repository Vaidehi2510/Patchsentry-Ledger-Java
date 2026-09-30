package com.patchsentry.ledger.web;

import com.patchsentry.ledger.config.LedgerProperties;
import com.patchsentry.ledger.core.RunnerTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/** Guards {@code /api/admin/*} with a shared admin token, compared in constant time. */
public class AdminAuthFilter extends OncePerRequestFilter {

    private final LedgerProperties properties;
    private final ProblemResponses problems;

    public AdminAuthFilter(LedgerProperties properties, ProblemResponses problems) {
        this.properties = properties;
        this.problems = problems;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.adminEnabled()) {
            problems.write(response, HttpStatus.SERVICE_UNAVAILABLE, "Admin API is disabled until LEDGER_ADMIN_TOKEN is set");
            return;
        }
        if (!RunnerTokens.constantTimeEquals(properties.adminToken(), BearerTokens.from(request))) {
            problems.write(response, HttpStatus.UNAUTHORIZED, "A valid admin token is required");
            return;
        }
        chain.doFilter(request, response);
    }
}
