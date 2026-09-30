package com.patchsentry.ledger.service;

import java.util.UUID;

public record IssuedRunnerToken(UUID tokenId, String token, String displayPrefix) {

    @Override
    public String toString() {
        return "IssuedRunnerToken[tokenId=" + tokenId + ", displayPrefix=" + displayPrefix + "]";
    }
}
