package com.patchsentry.ledger.web;

import com.patchsentry.ledger.persistence.ProjectEntity;
import com.patchsentry.ledger.service.IssuedRunnerToken;
import com.patchsentry.ledger.service.ProjectService;
import com.patchsentry.ledger.service.RunQueryService;
import com.patchsentry.ledger.web.dto.CreateProjectRequest;
import com.patchsentry.ledger.web.dto.ProjectResponse;
import com.patchsentry.ledger.web.dto.RunDetailResponse;
import com.patchsentry.ledger.web.dto.RunSummaryResponse;
import com.patchsentry.ledger.web.dto.TokenResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ProjectService projects;
    private final RunQueryService queries;

    public AdminController(ProjectService projects, RunQueryService queries) {
        this.projects = projects;
        this.queries = queries;
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectResponse> createProject(@Valid @RequestBody CreateProjectRequest body) {
        ProjectEntity project = projects.create(body.repository(), body.name());
        return ResponseEntity.created(URI.create("/api/admin/projects/" + project.getId()))
                .body(ProjectResponse.from(project));
    }

    @PostMapping("/projects/{projectId}/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public TokenResponse issueToken(@PathVariable("projectId") UUID projectId) {
        IssuedRunnerToken issued = projects.issueToken(projectId);
        return new TokenResponse(issued.tokenId(), issued.token(), issued.displayPrefix(),
                "Copy this token now. Only its hash is stored, so it cannot be shown again.");
    }

    @DeleteMapping("/tokens/{tokenId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeToken(@PathVariable("tokenId") UUID tokenId) {
        projects.revokeToken(tokenId);
    }

    @GetMapping("/projects/{projectId}/runs")
    public List<RunSummaryResponse> listRuns(
            @PathVariable("projectId") UUID projectId,
            @RequestParam(name = "pr", required = false) Integer prNumber,
            @RequestParam(name = "limit", defaultValue = "20") int limit) {
        return queries.list(projectId, prNumber, limit);
    }

    @GetMapping("/projects/{projectId}/runs/{recordId}")
    public RunDetailResponse getRun(@PathVariable("projectId") UUID projectId,
            @PathVariable("recordId") String recordId) {
        return queries.get(projectId, recordId);
    }
}
