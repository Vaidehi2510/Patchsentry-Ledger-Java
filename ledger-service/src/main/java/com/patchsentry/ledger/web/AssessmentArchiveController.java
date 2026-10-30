package com.patchsentry.ledger.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patchsentry.ledger.persistence.ProjectRepository;
import com.patchsentry.ledger.service.ConflictException;
import com.patchsentry.ledger.service.NotFoundException;
import com.patchsentry.ledger.core.ValidationException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Scoped archival, not an independent attestation that the uploaded tests executed. */
@RestController
@RequestMapping("/api/runner")
public class AssessmentArchiveController {
 private final JdbcTemplate db; private final ObjectMapper json; private final ProjectRepository projects;
 public AssessmentArchiveController(JdbcTemplate db,ObjectMapper json,ProjectRepository projects){this.db=db;this.json=json;this.projects=projects;}
 @GetMapping("/project")
 public Map<String,Object> project(@RequestAttribute(RunnerAuthFilter.PROJECT_ATTRIBUTE) UUID id){var p=projects.findById(id).orElseThrow(()->new NotFoundException("Project unavailable"));return Map.of("projectId",id,"repository",p.getRepository(),"capabilities",List.of("assessment-archive-v1"));}
 private String hash(byte[] value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}catch(Exception e){throw new IllegalStateException("SHA-256 unavailable");}}
 private void require(boolean valid,String message){if(!valid)throw new ValidationException(List.of(message));}
 @PutMapping("/assessments/{recordId}/{digest}")
 @Transactional
 public Map<String,Object> archive(@PathVariable String recordId,@PathVariable String digest,@RequestBody byte[] bytes,@RequestAttribute(RunnerAuthFilter.PROJECT_ATTRIBUTE) UUID id) throws Exception {
  require(bytes.length>0&&bytes.length<=512000,"Assessment payload must fit within 512KB");
  require(recordId.matches("[a-f0-9]{64}")&&digest.matches("[a-f0-9]{64}")&&hash(bytes).equals(digest),"Payload digest does not match the exact uploaded bytes");
  JsonNode report=json.readTree(bytes);require(report.path("schemaVersion").asInt()==1&&recordId.equals(report.path("recordId").asText()),"Assessment record identity mismatch");
  var p=projects.findById(id).orElseThrow(()->new NotFoundException("Project unavailable"));String repository=report.path("repository").asText(),revision=report.path("revision").asText();
  require(p.getRepository().equalsIgnoreCase(repository)&&revision.matches("[a-fA-F0-9]{40,64}"),"Assessment must match the runner project and a source revision");
  require(report.path("phase").asText().equals("completed")&&report.path("completedAt").isTextual()&&!report.path("completedAt").asText().isBlank(),"Only completed assessments can be archived");
  boolean pr=report.hasNonNull("pr"),target=report.hasNonNull("target");require(pr!=target,"Choose exactly one PR or build/release subject");
  String subject;
  if(pr){require(report.path("pr").path("number").asInt()>0,"A real PR number is required");subject="pr:"+report.path("pr").path("number").asInt();}
  else{var t=report.path("target");require(List.of("build","release").contains(t.path("kind").asText())&&t.path("artifactSha256").asText().matches("[a-f0-9]{64}")&&!t.path("name").asText().isBlank()&&!t.path("platform").asText().isBlank(),"A complete build/release identity is required");subject=t.path("kind").asText()+":"+t.path("name").asText()+":"+t.path("platform").asText()+":"+t.path("artifactSha256").asText();}
  String identity=hash((repository.toLowerCase()+"\n"+revision.toLowerCase()+"\n"+subject).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  // Lock the project so simultaneous first snapshots cannot bind a record differently.
  db.queryForObject("SELECT id FROM projects WHERE id=? FOR UPDATE",UUID.class,id);
  var old=db.queryForList("SELECT identity_digest FROM assessment_archive WHERE project_id=? AND record_id=? LIMIT 1",String.class,id,recordId);
  if(!old.isEmpty()&&!old.get(0).equals(identity))throw new ConflictException("An assessment record cannot change its subject or revision");
  int count=db.update("INSERT INTO assessment_archive(project_id,record_id,payload_digest,identity_digest,repository,revision,payload) VALUES(?,?,?,?,?,?,?) ON CONFLICT DO NOTHING",id,recordId,digest,identity,repository,revision,bytes);
  return Map.of("projectId",id,"recordId",recordId,"payloadDigest",digest,"archived",true,"duplicate",count==0);
 }
 @GetMapping("/assessments/{recordId}/{digest}")
 public Map<String,Object> receipt(@PathVariable String recordId,@PathVariable String digest,@RequestAttribute(RunnerAuthFilter.PROJECT_ATTRIBUTE) UUID id){var rows=db.queryForList("SELECT record_id,payload_digest,revision,received_at FROM assessment_archive WHERE project_id=? AND record_id=? AND payload_digest=?",id,recordId,digest);if(rows.isEmpty())throw new NotFoundException("Assessment receipt unavailable");return Map.of("projectId",id,"recordId",recordId,"payloadDigest",digest,"archived",true,"revision",rows.get(0).get("revision"));}
}
