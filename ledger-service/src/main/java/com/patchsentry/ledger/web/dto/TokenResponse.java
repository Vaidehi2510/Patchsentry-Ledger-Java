package com.patchsentry.ledger.web.dto;

import java.util.UUID;

public record TokenResponse(UUID tokenId, String token, String displayPrefix, String note) {
}
