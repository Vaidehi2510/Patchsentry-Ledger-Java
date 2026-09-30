package com.patchsentry.ledger.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

final class BearerTokens {

    private BearerTokens() {
    }

    static String from(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return header.substring(7).trim();
    }
}
