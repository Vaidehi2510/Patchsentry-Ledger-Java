package com.patchsentry.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * End to end against real Postgres, MongoDB, and Redis containers: HTTP -> filters ->
 * service -> Flyway schema -> both stores -> query API.
 */
@SpringBootTest(properties = {
        "ledger.admin-token=integration-admin-token",
        "ledger.rate-limit-per-minute=5"})
@AutoConfigureMockMvc
@Testcontainers
class LedgerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7.0");

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    private static final String ADMIN = "Bearer integration-admin-token";
    private static final String REVISION = "a1".repeat(20);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    private static String newRecordId() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "");
    }

    private static String newRepository() {
        return "acme/shop-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode read(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String createProject(String repository) throws Exception {
        return read(mvc.perform(post("/api/admin/projects").header(AUTHORIZATION, ADMIN)
                        .contentType(APPLICATION_JSON)
                        .content("{\"repository\":\"%s\",\"name\":\"Shop\"}".formatted(repository)))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode issueToken(String projectId) throws Exception {
        return read(mvc.perform(post("/api/admin/projects/{id}/tokens", projectId).header(AUTHORIZATION, ADMIN))
                .andExpect(status().isCreated()));
    }

    /** Lowercase enums and an unknown field, as the Patchsentry runner sends them. */
    private static String report(String recordId, String repository, String revision, String e2eStatus) {
        return """
                {
                  "schemaVersion": 1,
                  "recordId": "%s",
                  "repository": "%s",
                  "pr": { "number": 12, "title": "Add express checkout" },
                  "revision": "%s",
                  "phase": "completed",
                  "completedAt": "2026-09-30T12:00:00Z",
                  "checks": [
                    { "id": "unit", "name": "Unit tests", "method": "automated", "required": true, "status": "passed" },
                    { "id": "e2e", "name": "Checkout journey", "method": "automated", "required": true, "status": "%s" }
                  ],
                  "findings": [
                    { "title": "Pay button overflows on mobile", "severity": "medium", "file": "Checkout.jsx",
                      "description": "The button leaves the 390px viewport.", "suggestedFix": "Wrap the row." }
                  ],
                  "limitations": ["Synthetic fixture"],
                  "ai": { "backend": "local", "model": "qa-local", "costUsd": 0, "calls": 3 }
                }
                """.formatted(recordId, repository, revision, e2eStatus);
    }

    private ResultActions upload(String token, String recordId, String body) throws Exception {
        return mvc.perform(put("/api/runner/runs/{recordId}", recordId)
                .header(AUTHORIZATION, "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(body));
    }

    @Test
    void uploadsAreIdempotentAndQueryableAcrossBothStores() throws Exception {
        String repository = newRepository();
        String projectId = createProject(repository);
        String token = issueToken(projectId).get("token").asText();
        String recordId = newRecordId();

        upload(token, recordId, report(recordId, repository, REVISION, "failed"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.score").value(50))
                .andExpect(jsonPath("$.outcome").value("FAILED"));

        upload(token, recordId, report(recordId, repository, REVISION, "passed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(false))
                .andExpect(jsonPath("$.score").value(100))
                .andExpect(jsonPath("$.outcome").value("PASSED"));

        mvc.perform(get("/api/admin/projects/{id}/runs", projectId).header(AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uploadCount").value(2));

        mvc.perform(get("/api/admin/projects/{id}/runs/{recordId}", projectId, recordId).header(AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checks.length()").value(2))
                .andExpect(jsonPath("$.evidenceAvailable").value(true))
                .andExpect(jsonPath("$.findings[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$.ai.model").value("qa-local"));
    }

    @Test
    void aRecordIdStaysBoundToItsRevision() throws Exception {
        String repository = newRepository();
        String token = issueToken(createProject(repository)).get("token").asText();
        String recordId = newRecordId();

        upload(token, recordId, report(recordId, repository, REVISION, "passed")).andExpect(status().isCreated());
        upload(token, recordId, report(recordId, repository, "b2".repeat(20), "passed"))
                .andExpect(status().isConflict());
    }

    @Test
    void aTokenCannotUploadForAnotherRepository() throws Exception {
        String token = issueToken(createProject(newRepository())).get("token").asText();
        String recordId = newRecordId();

        upload(token, recordId, report(recordId, "someone/else", REVISION, "passed"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidReportsListEveryProblem() throws Exception {
        String repository = newRepository();
        String token = issueToken(createProject(repository)).get("token").asText();
        String recordId = newRecordId();
        String broken = report(recordId, repository, "not-a-sha", "passed").replace("\"number\": 12", "\"number\": 0");

        JsonNode problem = read(upload(token, recordId, broken).andExpect(status().isBadRequest()));
        assertThat(problem.get("errors")).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void unknownRevokedAndMissingCredentialsAreRejected() throws Exception {
        String repository = newRepository();
        String recordId = newRecordId();
        String body = report(recordId, repository, REVISION, "passed");

        upload("psl_" + "x".repeat(43), recordId, body).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/projects").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());

        JsonNode issued = issueToken(createProject(repository));
        mvc.perform(delete("/api/admin/tokens/{id}", issued.get("tokenId").asText()).header(AUTHORIZATION, ADMIN))
                .andExpect(status().isNoContent());
        upload(issued.get("token").asText(), recordId, body).andExpect(status().isUnauthorized());
    }

    @Test
    void eachRunnerTokenIsRateLimitedAcrossRequests() throws Exception {
        String repository = newRepository();
        String token = issueToken(createProject(repository)).get("token").asText();
        String recordId = newRecordId();
        String body = report(recordId, repository, REVISION, "passed");

        // Limit is 5 per minute. 12 requests guarantee a full window even if a minute boundary is crossed.
        int limited = 0;
        for (int i = 0; i < 12; i++) {
            if (upload(token, recordId, body).andReturn().getResponse().getStatus() == 429) {
                limited++;
            }
        }
        assertThat(limited).isPositive();
    }

    private String sha(String text) throws Exception {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}

    @Test
    void portalNativeArchivesPreserveBuildsAndAreScopedImmutableAndIdempotent() throws Exception {
        String repository=newRepository();var project=createProject(repository);String token=issueToken(project).get("token").asText(),recordId=newRecordId();
        var body=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(report(recordId,repository,REVISION,"passed"));
        body.put("phase","completed");body.put("completedAt","2026-10-30T12:00:00Z");body.remove("pr");var target=body.putObject("target");target.put("kind","release");target.put("name","v1");target.put("platform","web");target.put("artifactSha256","d".repeat(64));String bytes=json.writeValueAsString(body),digest=sha(bytes),url="/api/runner/assessments/"+recordId+"/"+digest;
        mvc.perform(get("/api/runner/project").header(AUTHORIZATION,"Bearer "+token)).andExpect(status().isOk()).andExpect(jsonPath("$.repository").value(repository)).andExpect(jsonPath("$.capabilities[0]").value("assessment-archive-v1"));
        mvc.perform(put(url).header(AUTHORIZATION,"Bearer "+token).contentType(APPLICATION_JSON).content(bytes)).andExpect(status().isOk()).andExpect(jsonPath("$.payloadDigest").value(digest)).andExpect(jsonPath("$.duplicate").value(false));
        mvc.perform(put(url).header(AUTHORIZATION,"Bearer "+token).contentType(APPLICATION_JSON).content(bytes)).andExpect(status().isOk()).andExpect(jsonPath("$.duplicate").value(true));
        mvc.perform(get(url).header(AUTHORIZATION,"Bearer "+token)).andExpect(status().isOk());
        assertThat(mvc.perform(get(url+"/payload").header(AUTHORIZATION,"Bearer "+token)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).isEqualTo(bytes);
        String other=issueToken(createProject(newRepository())).get("token").asText();mvc.perform(get(url).header(AUTHORIZATION,"Bearer "+other)).andExpect(status().isNotFound());
        String second=issueToken(project).get("token").asText();mvc.perform(put(url).header(AUTHORIZATION,"Bearer "+second).contentType(APPLICATION_JSON).content(bytes+" ")).andExpect(status().isBadRequest());
        body.put("revision","b2".repeat(20));String changed=json.writeValueAsString(body);mvc.perform(put("/api/runner/assessments/"+recordId+"/"+sha(changed)).header(AUTHORIZATION,"Bearer "+second).contentType(APPLICATION_JSON).content(changed)).andExpect(status().isConflict());
    }

    @Test
    void readinessReportsAllThreeBackingStores() throws Exception {
        mvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
