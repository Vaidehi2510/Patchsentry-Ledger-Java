package com.patchsentry.ledger.evidence;

import com.patchsentry.ledger.core.AiUsage;
import com.patchsentry.ledger.core.Finding;
import com.patchsentry.ledger.core.RunReport;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Variable-shape evidence for a run (AI findings, limitations, model usage), kept
 * in MongoDB so the relational schema only holds what is queried and scored.
 * Keyed by the same record ID, so a retried upload overwrites instead of duplicating.
 */
@Document(collection = "run_evidence")
public record RunEvidenceDocument(
        @Id String recordId,
        @Indexed String projectId,
        String revision,
        List<Finding> findings,
        List<String> limitations,
        AiUsage ai,
        Instant updatedAt) {

    public static RunEvidenceDocument from(RunReport report, UUID projectId, Instant now) {
        return new RunEvidenceDocument(report.recordId(), projectId.toString(), report.revision(),
                report.findings(), report.limitations(), report.ai(), now);
    }
}
