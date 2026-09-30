package com.patchsentry.ledger.evidence;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RunEvidenceRepository extends MongoRepository<RunEvidenceDocument, String> {
}
