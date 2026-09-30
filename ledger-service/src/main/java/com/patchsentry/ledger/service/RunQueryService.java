package com.patchsentry.ledger.service;

import com.patchsentry.ledger.evidence.RunEvidenceRepository;
import com.patchsentry.ledger.persistence.ProjectRepository;
import com.patchsentry.ledger.persistence.QaRunEntity;
import com.patchsentry.ledger.persistence.QaRunRepository;
import com.patchsentry.ledger.web.dto.RunDetailResponse;
import com.patchsentry.ledger.web.dto.RunSummaryResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RunQueryService {

    static final int MAX_PAGE = 100;

    private final ProjectRepository projects;
    private final QaRunRepository runs;
    private final RunEvidenceRepository evidence;

    public RunQueryService(ProjectRepository projects, QaRunRepository runs, RunEvidenceRepository evidence) {
        this.projects = projects;
        this.runs = runs;
        this.evidence = evidence;
    }

    public List<RunSummaryResponse> list(UUID projectId, Integer prNumber, int limit) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("Project " + projectId + " not found");
        }
        Pageable page = PageRequest.of(0, Math.max(1, Math.min(limit, MAX_PAGE)));
        List<QaRunEntity> found = prNumber == null
                ? runs.findByProjectIdOrderByUpdatedAtDesc(projectId, page)
                : runs.findByProjectIdAndPrNumberOrderByUpdatedAtDesc(projectId, prNumber, page);
        return found.stream().map(RunSummaryResponse::from).toList();
    }

    public RunDetailResponse get(UUID projectId, String recordId) {
        QaRunEntity run = runs.findByRecordIdAndProjectId(recordId, projectId)
                .orElseThrow(() -> new NotFoundException("Run " + recordId + " not found"));
        return RunDetailResponse.from(run, evidence.findById(recordId).orElse(null));
    }
}
