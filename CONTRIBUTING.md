# Contributing to Patchsentry Ledger

Ledger is an optional Java report/archive service in the Patchsentry project.
Start with the [README](README.md). It can be developed locally without a deployed
website or a company integration.

## Useful first contributions

Clearer API examples, reproducible setup notes, synthetic validator/scoring edge
cases, error-message improvements and focused tests are welcome. Look for a
`good first issue`, or describe a problem and propose a bounded task. Documentation
and domain/test design contributions are useful even if Java is not your primary
language.

## Repository map

- `ledger-core`: framework-free domain, validation, scoring and token helpers.
- `ledger-service`: Spring Boot HTTP/authentication, storage and archive adapters.
- [QA engine](https://github.com/Vaidehi2510/QA-testbot): actual test execution.
- [Website](https://github.com/Vaidehi2510/patchsentry-web): projects, current reports
  and the optional scoped archive connection.

## Local setup and checks

Use JDK 21 and Maven 3.9+. Docker is needed for Testcontainers and the local service
stack. A small core-domain change can be checked first with:

```sh
mvn -pl ledger-core test
```

Service/integration changes should also run:

```sh
mvn -B verify
```

Use the README's Docker Compose instructions only with a disposable local stack
and test-only credentials. Tests create real temporary datastores where indicated;
Mockito responses test contracts and must not be described as a real remote service.
The Kubernetes CI job uses a disposable `kind` cluster, not a production deployment.

Keep schema changes compatible with retained report/archive bytes and add explicit
migration/recovery notes when the data format changes. Token scopes, project
boundaries, exact payload digests and retry/cancellation semantics need regression
checks when changed. A stored archive acknowledgment is not proof that the product
passed QA or that the uploader's build provenance is independently verified.

## Propose and submit a change

1. Search this repository's issues for related work. For a substantial change,
   describe the problem and proposed scope in an issue before investing much time.
   Small fixes can go straight to a pull request.
2. Fork the repository, create a focused branch, and keep unrelated changes out.
3. Explain the user-visible problem, the resulting behavior and the checks you
   actually ran. If a check was not run, state that and why.
4. Include reproduction steps and synthetic evidence for a bug. For UI changes,
   check keyboard use and a narrow viewport; include a screenshot when useful.
5. Respond to review questions and credit anyone whose work informed the change.

A maintainer reviews scope, behavior and evidence before merging. Review is
best-effort; there is no promised turnaround. Contributors are not expected to
configure paid services or obtain production credentials to propose a change.

## Choose checks that match the change

For wording or policy edits, check links, commands and formatting; a full
application test run is usually unnecessary. For behavior changes, run the relevant
existing tests and add a regression only when it verifies the behavior at risk.
Run the repository's required CI checks before relying on a release. Passing
fixture tests does not prove live-provider interoperability, whole-product test
coverage or staffing savings.

Use synthetic accounts and disposable environments. Never commit `.env` files,
tokens, private repository content, customer reports, screenshots containing real
user data, or production database exports. Do not start paid model/device sessions,
send notifications, or enable production schedules as part of ordinary testing.
The QA bot's product-source access must remain read-only; proposed fixes and tests
must not silently edit, push to or merge a connected product repository.

If an AI tool assisted, briefly describe its role and how you checked the result.
Label mocked model responses and integrations. You are responsible for reviewing
the submitted code and its claims; an automated agent role is not evidence of
participation by another person.

## Community, security and license

Follow the [code of conduct](CODE_OF_CONDUCT.md). Report possible vulnerabilities
using [SECURITY.md](SECURITY.md), rather than a public issue containing an exploit
or sensitive data. General project questions can use a public issue, or
[patchsentry@gmail.com](mailto:patchsentry@gmail.com) when they need to be private.

Contributions are submitted under this repository's [MIT License](LICENSE).
Keep any third-party copyright and license notices with their material. Identify
external code/assets and their source so a maintainer can review compatibility.
