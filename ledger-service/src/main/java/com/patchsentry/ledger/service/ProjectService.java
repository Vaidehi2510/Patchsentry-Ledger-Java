package com.patchsentry.ledger.service;

import com.patchsentry.ledger.core.RunnerTokens;
import com.patchsentry.ledger.persistence.ProjectEntity;
import com.patchsentry.ledger.persistence.ProjectRepository;
import com.patchsentry.ledger.persistence.RunnerTokenEntity;
import com.patchsentry.ledger.persistence.RunnerTokenRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final RunnerTokenRepository tokens;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, RunnerTokenRepository tokens, Clock clock) {
        this.projects = projects;
        this.tokens = tokens;
        this.clock = clock;
    }

    @Transactional
    public ProjectEntity create(String repository, String name) {
        // A concurrent duplicate still hits the unique index and maps to 409.
        if (projects.existsByRepositoryIgnoreCase(repository)) {
            throw new ConflictException("A project for " + repository + " already exists");
        }
        return projects.save(new ProjectEntity(UUID.randomUUID(), repository, name.strip(), clock.instant()));
    }

    @Transactional
    public IssuedRunnerToken issueToken(UUID projectId) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("Project " + projectId + " not found");
        }
        RunnerTokens.IssuedToken issued = RunnerTokens.issue();
        UUID tokenId = UUID.randomUUID();
        tokens.save(new RunnerTokenEntity(tokenId, projectId, issued.hash(), issued.displayPrefix(), clock.instant()));
        return new IssuedRunnerToken(tokenId, issued.plaintext(), issued.displayPrefix());
    }

    @Transactional
    public void revokeToken(UUID tokenId) {
        RunnerTokenEntity token = tokens.findById(tokenId)
                .orElseThrow(() -> new NotFoundException("Token " + tokenId + " not found"));
        token.revoke(clock.instant());
    }
}
