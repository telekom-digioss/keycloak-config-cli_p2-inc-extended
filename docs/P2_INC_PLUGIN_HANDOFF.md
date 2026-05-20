# P2-Inc Organizations Basics For Maintainer Review

## Purpose Of This Document

This document describes the implementation that was introduced by commit [3e182d1d9b6d90c99067f09cd158d7fe01338a1e](https://github.com/telekom-digioss/keycloak-config-cli_p2-inc-extended/commit/3e182d1d9b6d90c99067f09cd158d7fe01338a1e) (`P2-Inc organizations basics`).

Its goal is not to argue that the implementation is perfect. Its goal is to make it easy for an adorsys maintainer to decide whether this kind of extension is desirable in `keycloak-config-cli` core, and if yes, in what form.

This document is intentionally limited to the content of that commit.

## Executive Summary

The commit adds a dedicated import mode for Phase Two organization data.

The implementation does not extend the existing `RealmImport` model and does not try to merge P2-specific structures into the normal import pipeline. Instead, it introduces a second, explicit execution path:

1. normal mode continues to use `KeycloakImportProvider` and `RealmImportService`
2. P2 mode is activated through `p2.import.enabled=true`
3. in P2 mode, `KeycloakConfigRunner` delegates to `P2ImportService` and returns before the normal core import path starts

The commit therefore treats P2 input as a separate document family rather than as an extension of the existing realm import schema.

## What Was Added In The Commit

### Core touches

- `src/main/java/de/adorsys/keycloak/config/KeycloakConfigRunner.java`
- `src/main/java/de/adorsys/keycloak/config/provider/KeycloakImportProvider.java`
- `src/main/resources/application.properties`

### New P2-specific slice

- `src/main/java/de/adorsys/keycloak/config/phasetwo/model/P2OrganizationRepresentation.java`
- `src/main/java/de/adorsys/keycloak/config/phasetwo/properties/P2ImportConfigProperties.java`
- `src/main/java/de/adorsys/keycloak/config/phasetwo/properties/P2ManagementConfigProperties.java`
- `src/main/java/de/adorsys/keycloak/config/phasetwo/provider/P2ImportProvider.java`
- `src/main/java/de/adorsys/keycloak/config/phasetwo/resource/P2OrganizationsResource.java`
- `src/main/java/de/adorsys/keycloak/config/phasetwo/repository/P2OrganizationRepository.java`
- `src/main/java/de/adorsys/keycloak/config/phasetwo/service/P2ImportService.java`
- `src/main/java/de/adorsys/keycloak/config/phasetwo/service/P2OrganizationImportService.java`

### Tests and fixtures

- `src/test/java/de/adorsys/keycloak/config/P2_KeycloakConfigRunnerTest.java`
- `src/test/java/de/adorsys/keycloak/config/phasetwo/service/P2_ImportServiceTest.java`
- `src/test/java/de/adorsys/keycloak/config/phasetwo/service/P2_OrganizationImportServiceTest.java`
- `src/test/java/de/adorsys/keycloak/config/provider/P2_KeycloakImportProviderIT.java`
- `src/test/resources/import-files/import/invalid/P2_prefixed_data.json`

### Supporting change

- `.gitignore` now ignores `*.log`

## Architectural Idea Behind The Commit

The design makes one central choice:

`P2` data is not modeled as a small optional sub-structure inside standard `RealmImport` documents. It is modeled as a different input contract with its own parser, its own validation rules, its own configuration properties, and its own execution service.

This choice reduces the risk of contaminating the normal import schema with product-specific data. The price is that the runtime now contains a second import orchestration path.

In practical terms, the commit says:

- standard imports remain standard imports
- P2 imports must be separate files
- a dedicated switch decides which engine runs
- this is a intermediate approch to reduce risks about maintenance as long not good way of `functional plugin extensions` is available.

## Runtime Flow Introduced By The Commit

### Standard mode

If `p2.import.enabled=false`, `KeycloakConfigRunner` behaves as before:

1. read configured import locations
2. parse through `KeycloakImportProvider`
3. build `KeycloakImport` / `RealmImport`
4. delegate to `RealmImportService`

### P2 mode

If `p2.import.enabled=true`, `KeycloakConfigRunner` changes behavior:

1. read configured import locations
2. resolve `P2ImportService` lazily through `ObjectProvider`
3. call `P2ImportService.run(importLocations)`
4. return immediately
5. do not execute `KeycloakImportProvider.readFromLocations(...)`
6. do not execute `RealmImportService.doImport(...)`

This is the most important architectural fact for a maintainer review. The commit does not add a late plugin hook after normal import. It introduces an alternate import branch at runner level.

## Why The Commit Also Changes The Standard Provider

The commit adds `assertNoP2Data(...)` to `KeycloakImportProvider`.

That guard recursively traverses standard import documents and rejects any occurrence of the top-level key `P2`, including nested occurrences. The resulting error explicitly tells the user to move P2 data into a dedicated plugin-only file.

This matters because the commit is only coherent if the two file families are kept separate:

- standard documents go through `KeycloakImportProvider`
- P2 documents go through `P2ImportProvider`

Without the guard, the boundary between both modes would be underspecified and error-prone.

## Scope Of The Implemented P2 Feature

The commit implements only the first P2 organization slice.

Supported business scope:

- import P2 organization documents
- validate that the document contains only `realm` and `P2`
- validate that `P2.organizations` exists and is a list
- map each organization document into a P2-specific representation
- synchronize organizations through create/update/delete behavior

Not implemented in that commit (not relavant for adressing the question):

- generic P2 plugin infrastructure
- mixed standard plus P2 document processing in the same run
- other P2 resource types
- richer P2 structures under `P2` besides `organizations`

## Configuration Surface Added By The Commit

Two new property groups are introduced.

`p2.import.enabled`

- default `false`
- enables the P2 execution branch

`p2.import.managed.organization`

- default `full`
- supported values: `FULL`, `NO_DELETE`
- controls whether missing P2 organizations are deleted or only created/updated

This property split keeps the activation flag and the resource management policy separate.

## File Format Assumptions In The Commit

The commit expects P2 files to look conceptually like this:

```yaml
realm: my-realm
P2:
    organizations:
        - name: acme
            displayName: Acme
            url: https://example.org
            domains:
                - example.org
            attributes:
                tier:
                    - enterprise
```

Rules enforced by the commit:

- `realm` must exist and be non-empty
- top-level keys are limited to `realm` and `P2`
- `P2` must be an object
- inside `P2`, only `organizations` is accepted
- each organization must have a non-empty `name`

## Logical Split Between Components

### `P2ImportProvider`

Responsibilities:

- resolve import locations with the same resource pattern infrastructure used by the core importer
- apply exclude rules and hidden-file handling
- apply variable substitution and JavaScript substitution exactly like the core importer
- parse YAML or JSON into raw `Map<String, Object>` documents

Notably, this provider does not produce `RealmImport`. It intentionally stops at a raw map representation.

### `P2ImportService`

Responsibilities:

- iterate through all P2 documents from all configured locations
- validate the P2-only document envelope
- extract the `P2` object
- route the supported data subset to the next service

This service is the schema gatekeeper for the P2-specific branch.

### `P2OrganizationImportService`

Responsibilities:

- convert raw list items into `P2OrganizationRepresentation`
- validate business identity through `name`
- load existing P2 organizations
- apply the management mode (`FULL` or `NO_DELETE`)
- decide create vs update vs delete

This service contains the synchronization logic.

### `P2OrganizationRepository`

Responsibilities:

- call the Phase Two admin endpoints under `/admin/realms/{realm}/orgs`
- list organizations
- create organizations
- update organizations
- delete organizations

This is the remote integration boundary.

## Why The Design Might Be Attractive For Core

There are real benefits in the chosen structure.

1. Standard `RealmImport` remains untouched by product-specific fields.
2. The P2 code lives in a clearly isolated package subtree.
3. The normal import path explicitly refuses P2 input, which avoids ambiguous semantics.
4. Reuse of existing infrastructure is high where it is generic and safe: resource discovery, variable substitution, JavaScript substitution, Spring configuration, REST proxy pattern, repository pattern, and test style.

## Why A Maintainer Might Still Reject Core Inclusion In This Form

There are also clear reasons to hesitate.

1. The commit introduces a second orchestration branch in `KeycloakConfigRunner`, which is a stronger core change than a plugin hook or extension point.
2. The feature is product-specific and not obviously reusable for non-Phase-Two users.
3. The P2 import mode is mutually exclusive with the standard import path in one run.
4. The core provider now contains awareness of a product-specific top-level key, even though only as a protective rejection rule.
5. The feature is currently narrow. It handles only organizations basics, not a complete general-purpose external extension framework.
   (But they will be added by us for the the features of P2-Inc what we need)

## Maintainer Decision Framing

The real maintainer question is not only "Does this code work?".

The real question is:

"Does keycloak-config-cli want to support isolated product-specific import branches in core, provided they are clearly gated, test-covered, and prevented from leaking into standard imports?"

If the answer is `no`, the reason is likely architectural policy, not implementation quality.

If the answer is `yes`, the next decision is whether this exact P2 slice should live in core or whether core should first expose a more general extension mechanism and move the P2 logic behind that mechanism.

## Recommended Question To Ask The Maintainer

Suggested wording:

> We implemented a Phase Two organizations import as a separate, explicitly gated import branch. The normal import schema stays unchanged, standard imports reject `P2` documents, and all P2 logic is isolated under `de.adorsys.keycloak.config.phasetwo`. The only core touches are the runner-level branch, the standard-import rejection guard, and two configuration properties. Is this kind of isolated product-specific import mode something you would consider acceptable in `keycloak-config-cli` core, or would you prefer such support to stay outside core unless there is a more general extension mechanism first?

## Recommendation From The Current Implementation

Based on the structure of this commit alone, the strongest argument for upstreaming is the clean separation between standard imports and P2 imports.

The strongest argument against upstreaming is the runner-level fork in orchestration.

If the maintainer is open to the use case but not to the exact form, the likely compromise would be:

1. keep the schema separation idea
2. keep the package isolation idea
3. replace the runner-level branch with a more general extension mechanism

## Detailed Architecture Walkthrough

### High-Level Design

The implementation introduces a dedicated import mode for Phase Two organization data.

The design intentionally separates two worlds:

- standard `keycloak-config-cli` realm imports
- P2-specific organization imports

The separation is enforced in three places:

1. by configuration: `p2.import.enabled`
2. by control flow: `KeycloakConfigRunner` chooses one branch or the other
3. by schema protection: the standard importer rejects documents that contain `P2`

This means the commit is based on isolation, not on schema merging.

### End-To-End Flow

#### Normal import branch

When `p2.import.enabled=false`:

1. `KeycloakConfigRunner.run(...)` collects import locations from `import.files.locations`
2. `KeycloakImportProvider.readFromLocations(...)` parses documents into `KeycloakImport`
3. `RealmImportService.doImport(...)` processes each resulting `RealmImport`

#### P2 import branch

When `p2.import.enabled=true`:

1. `KeycloakConfigRunner.run(...)` collects the same import locations
2. it resolves `P2ImportService` via `ObjectProvider<P2ImportService>`
3. it calls `P2ImportService.run(importLocations)`
4. it returns immediately
5. the standard provider and the standard realm import service are skipped entirely

This is the single most important runtime fact of the implementation.

### Why The Runner Is Used As The Branch Point

The commit branches in `KeycloakConfigRunner`, not deeper in the service layer.

That choice has two consequences.

Positive:

- the standard `RealmImportService` does not need to understand P2 structures
- the standard import model does not have to be widened
- it is obvious which engine is active

Negative:

- the runner now contains product-specific orchestration logic
- standard and P2 imports cannot be composed in one execution pass

This tradeoff is central to any maintainer decision.

### Core Files Changed And Their Roles

#### `KeycloakConfigRunner`

Role in the commit:

- adds `P2ImportConfigProperties`
- resolves `P2ImportService` lazily through `ObjectProvider`
- activates the P2 branch when `p2.import.enabled=true`
- preserves existing behavior when the flag is `false`

What this means logically:

- P2 mode is not an add-on after standard import
- P2 mode is an alternate import engine

#### `KeycloakImportProvider`

Role in the commit:

- remains the parser for standard imports
- adds recursive `assertNoP2Data(...)`
- rejects any occurrence of the `P2` key in standard input files

What this means logically:

- the standard import contract is explicitly protected against P2 payloads
- the implementation does not rely on user discipline alone

#### `application.properties`

Role in the commit:

- adds `p2.import.enabled=false`
- adds `p2.import.managed.organization=full`

What this means logically:

- the feature is opt-in
- the deletion policy is configurable

### P2 Package Slice

The new functionality is intentionally concentrated under:

`de.adorsys.keycloak.config.phasetwo`

This package split is one of the strongest architectural qualities of the commit because it keeps product-specific logic physically separate from the existing core structure.

### Component-By-Component Walkthrough

#### `P2ImportConfigProperties`

Responsibilities:

- bind `p2.import.enabled`
- expose whether the P2 branch should be active
- ignore unknown sibling fields under the same prefix

This class is deliberately minimal. It acts only as an activation gate.

#### `P2ManagementConfigProperties`

Responsibilities:

- bind `p2.import.managed.organization`
- control resource lifecycle behavior for P2 organizations

Supported management modes:

- `FULL`: create, update, and delete missing organizations
- `NO_DELETE`: create and update only

This mirrors existing management-style conventions in `keycloak-config-cli` without forcing P2 through the standard managed-resource machinery.

#### `P2ImportProvider`

Responsibilities:

- resolve import locations using Spring `PathMatchingResourcePatternResolver`
- honor file excludes and hidden-file handling
- support authenticated resources
- support the same variable substitution settings as the core importer
- support the same JavaScript substitution mechanism as the core importer
- parse YAML or JSON documents into raw `Map<String, Object>` structures

Important design detail:

`P2ImportProvider` stops at raw document maps. It does not try to coerce the input into `RealmImport`.

This is intentional. It preserves schema freedom for the P2 branch and keeps the standard model untouched.

#### `P2ImportService`

Responsibilities:

- iterate over all P2 files returned by `P2ImportProvider`
- validate the outer document envelope
- extract and normalize the `P2` object
- reject unsupported top-level or nested P2 keys
- route `P2.organizations` to the organization import service

Validation enforced here:

- `realm` is required and non-empty
- only `realm` and `P2` are allowed at document top level
- `P2` must exist and be an object
- `P2.organizations` must exist
- only `organizations` is allowed under `P2`
- `P2.organizations` must be a list

This service is effectively the P2 document schema validator.

#### `P2OrganizationRepresentation`

Responsibilities:

- define the transport shape used between parser, service, and REST repository
- represent the P2 organization fields needed by the commit

Fields included by the commit:

- `id`
- `name`
- `displayName`
- `url`
- `realm`
- `domains`
- `attributes`

Important implication:

The business identity in this design is `name`, not Keycloak core `alias`.

#### `P2OrganizationsResource`

Responsibilities:

- describe the custom Phase Two admin endpoints through a JAX-RS proxy interface

Endpoints used:

- `GET /admin/realms/{realm}/orgs`
- `POST /admin/realms/{realm}/orgs`
- `PUT /admin/realms/{realm}/orgs/{id}`
- `DELETE /admin/realms/{realm}/orgs/{id}`

This is a key design point: the commit does not call native Keycloak `realm.organizations()` APIs. It calls the P2-specific `/orgs` API surface.

#### `P2OrganizationRepository`

Responsibilities:

- create the typed REST proxy through `KeycloakProvider.getCustomApiProxy(...)`
- encapsulate HTTP interaction details
- convert remote API failures into `ImportProcessingException`
- offer a repository-style interface to the service layer

Methods and intent:

- `getAll(realmName)`: fetch current P2 organizations
- `search(realmName, organizationName)`: find existing organization by `name`
- `create(realmName, organization)`: create new P2 organization
- `update(realmName, organization)`: update existing P2 organization by `id`
- `delete(realmName, organization)`: delete existing P2 organization by `id`

This follows the existing repository pattern already used elsewhere in the project.

#### `P2OrganizationImportService`

Responsibilities:

- map raw organization items into `P2OrganizationRepresentation`
- validate business-level required data
- compare desired organizations to current server state
- apply configured management mode
- perform create, update, and optional delete operations

Core logic:

1. deserialize raw list items into typed organization objects
2. validate that every organization has a non-empty `name`
3. load current organizations from the P2 repository
4. if management mode is `FULL`, delete server-side organizations whose names are absent from the import
5. for each imported organization, search by `name`
6. create if absent
7. otherwise patch existing data while preserving `id`
8. update only when there is an actual difference

This service therefore implements a name-based synchronization model.

### Input Contract Implied By The Commit

The commit accepts P2-only documents. A representative example is:

```yaml
realm: example-realm
P2:
    organizations:
        - name: acme
            displayName: Acme Inc.
            url: https://acme.example
            domains:
                - acme.example
            attributes:
                tier:
                    - gold
```

The implementation explicitly does not accept these forms in the standard branch:

- `P2` nested inside normal realm import documents
- extra unsupported keys below `P2`
- missing `realm`
- missing `P2.organizations`

### Why The Commit Reuses Existing Infrastructure

The P2 branch does not reimplement everything.

It deliberately reuses generic project infrastructure where reuse is safe:

- Spring configuration and conditional bean creation
- resource pattern resolution
- file exclusion rules
- hidden-file handling
- variable substitution
- JavaScript substitution
- `KeycloakProvider` custom API proxy creation
- repository/service layering
- `CloneUtil` for patching and deep equality checks

This is important for maintainability. The commit introduces a new business slice, but it avoids inventing a second technical framework inside the project.

### Tests Added By The Commit

#### `P2_KeycloakConfigRunnerTest`

Proves:

- when P2 mode is enabled, the runner delegates to `P2ImportService`
- the core `KeycloakImportProvider` and `RealmImportService` are not used
- when P2 mode is disabled, the existing core branch still runs

#### `P2_ImportServiceTest`

Proves:

- `P2ImportService` delegates `P2.organizations` to the organization import service
- unsupported keys under `P2` cause failure

#### `P2_OrganizationImportServiceTest`

Proves:

- create when missing
- update when changed
- delete missing organizations in `FULL` mode
- do not delete in `NO_DELETE` mode
- fail when required business identity is missing

#### `P2_KeycloakImportProviderIT`

Proves:

- standard import locations reject P2 data with an explicit error message

#### `P2_prefixed_data.json`

Purpose:

- fixture for negative validation of the standard import branch

### Important Architectural Consequences

#### Consequence 1: Strict separation of document families

The implementation is very strict about not allowing P2 payloads to drift into standard imports. This is good for clarity and reduces schema ambiguity.

#### Consequence 2: Runner-level product branching

The implementation places product-specific orchestration in a core runner class. This is the most invasive architectural decision in the commit.

#### Consequence 3: Independent evolution of the P2 schema

Because the P2 parser works on raw maps and a dedicated representation, the P2 schema can evolve without forcing the main `RealmImport` model to change.

#### Consequence 4: No mixed processing in a single pass

The commit chooses clarity over composition. One execution runs either standard import logic or P2 import logic, not both.

### Questions A Maintainer Should Ask

1. Is it acceptable for core to contain a product-specific runner branch if the code is strongly isolated and opt-in?
2. Is the `P2` rejection inside `KeycloakImportProvider` considered a reasonable protective boundary or an undesirable product-specific leak into core code?
3. Is a separate import branch acceptable as a tactical solution, or should core require a generic extension point first?
4. Is the current scope narrow enough to keep maintainable, or too narrow and product-specific to justify core inclusion?

### Balanced Assessment

#### Strong points

- clear package isolation
- no pollution of `RealmImport`
- explicit schema boundary between standard and P2 documents
- reuse of existing generic infrastructure
- focused tests for branch selection and P2 behavior

#### Weak points

- core runner now contains feature-specific branching
- feature is tailored to one external product integration
- standard and P2 imports are mutually exclusive in one execution
- the design introduces a new conceptual mode rather than a reusable extension framework

### Maintainer-Oriented Conclusion

The implementation is architecturally coherent if the project is willing to support a small number of isolated, opt-in, product-specific import modes in core.

The implementation is not a neutral extension mechanism. It is a concrete Phase Two integration slice.

Therefore the maintainer decision is primarily a product and architecture policy decision:

- accept the isolated P2 branch as an explicit core feature
- or reject the direct feature but keep the separation idea as input for a future generic extension mechanism

## Related Documents

- local issue template in this repository: [P2_INC_MAINTAINER-QUESTION.md](P2_INC_MAINTAINER-QUESTION.md)
- GitHub-rendered technical handoff for external references: [P2_INC_PLUGIN_HANDOFF.md](https://github.com/telekom-digioss/keycloak-config-cli_p2-inc-extended/blob/v6.5_p2-poc/docs/P2_INC_PLUGIN_HANDOFF.md)
