package com.patchsentry.ledger.web;

import com.patchsentry.ledger.service.IngestResult;
import com.patchsentry.ledger.service.RunIngestService;
import com.patchsentry.ledger.web.dto.IngestResponse;
import com.patchsentry.ledger.web.dto.RunUploadRequest;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runner")
public class RunnerController {

    private final RunIngestService ingest;

    public RunnerController(RunIngestService ingest) {
        this.ingest = ingest;
    }

    /** PUT, because the record ID makes the upload idempotent: 201 on create, 200 on update. */
    @PutMapping("/runs/{recordId}")
    public ResponseEntity<IngestResponse> upload(
            @PathVariable("recordId") String recordId,
            @RequestBody RunUploadRequest body,
            @RequestAttribute(RunnerAuthFilter.PROJECT_ATTRIBUTE) UUID projectId) {
        IngestResult result = ingest.ingest(projectId, recordId, body.toReport());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(IngestResponse.from(recordId, result));
    }
}
