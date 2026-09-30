package com.patchsentry.ledger.persistence;

import com.patchsentry.ledger.core.CheckMethod;
import com.patchsentry.ledger.core.CheckResult;
import com.patchsentry.ledger.core.CheckStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "check_results")
public class CheckResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_record_id", nullable = false)
    private QaRunEntity run;

    @Column(nullable = false)
    private int position;

    @Column(name = "check_id", nullable = false, length = 100)
    private String checkId;

    @Column(length = 300)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckMethod method;

    @Column(nullable = false)
    private boolean required;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CheckStatus status;

    @Column(length = 4000)
    private String details;

    protected CheckResultEntity() {
        // For JPA.
    }

    CheckResultEntity(QaRunEntity run, int position, CheckResult check) {
        this.run = run;
        this.position = position;
        this.checkId = check.id();
        this.name = check.name();
        this.method = check.method();
        this.required = check.required();
        this.status = check.status();
        this.details = check.details();
    }

    public CheckResult toCheckResult() {
        return new CheckResult(checkId, name, method, required, status, details);
    }
}
