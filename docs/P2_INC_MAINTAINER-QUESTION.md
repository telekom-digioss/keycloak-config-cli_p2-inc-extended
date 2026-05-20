# Question For keycloak-config-cli Maintainer

## What Phase Two keycloak-orgs Is

The Phase Two extension discussed here is `p2-inc/keycloak-orgs`:

- Project overview: [p2-inc/keycloak-orgs](https://github.com/p2-inc/keycloak-orgs)
- README: [README.md](https://github.com/p2-inc/keycloak-orgs/blob/main/README.md)
- Core SPI entry point: [OrganizationProvider.java](https://github.com/p2-inc/keycloak-orgs/blob/main/src/main/java/io/phasetwo/service/model/OrganizationProvider.java)
- Main admin resource: [OrganizationsResource.java](https://github.com/p2-inc/keycloak-orgs/blob/main/src/main/java/io/phasetwo/service/resource/OrganizationsResource.java)
- Import/export documentation: [docs/import-export.md](https://github.com/p2-inc/keycloak-orgs/blob/main/docs/import-export.md)

In broad terms, this extension turns organizations into first-class Keycloak concepts for single-realm multi-tenancy. It adds its own organization model, memberships, organization-specific roles, invitations, domains, organization-aware identity provider handling, custom admin resources, authentication components, token mappers, and import/export APIs.

In other words, it is not just a small UI add-on. It is a fairly deep Keycloak extension layer for multi-tenant SaaS use cases, built around custom organization data and APIs rather than around plain groups or one-realm-per-tenant patterns.

This extension is for use very important as long keycloak core do not support the required separation of role to be organization specific.

## Why We Built A Separate P2 Import Slice In keycloak-config-cli

Because `keycloak-orgs` introduces its own resource model and API surface, we did not want to force that structure into the normal `RealmImport` schema of `keycloak-config-cli` without discussion / allignment with the maintainers of adorsys keycloak config client.

The implementation described below therefore treats P2 data as a separate document family with its own parsing and synchronization path.

## Context

We implemented a first, intentionally narrow integration slice for Phase Two organizations in commit [3e182d1d9b6d90c99067f09cd158d7fe01338a1e](https://github.com/telekom-digioss/keycloak-config-cli_p2-inc-extended/commit/3e182d1d9b6d90c99067f09cd158d7fe01338a1e) (`P2-Inc organizations basics`).

The implementation goal was to keep product-specific logic isolated while avoiding changes to the existing standard `RealmImport` schema.

## Short Version Of The Question

Would you consider it acceptable to include an explicitly gated, product-specific import mode like this in `keycloak-config-cli` core, or would you prefer such support to remain outside core unless there is a more general extension mechanism first?

Would you agree to have some keycloak specific extension at all contained in your config client ?

## What The Implementation Does

The implementation introduces a dedicated P2 import mode instead of extending normal `RealmImport` documents.

In concrete terms:

1. Standard imports remain unchanged.
2. A new flag, `p2.import.enabled`, activates a separate P2 import branch.
3. In that branch, `KeycloakConfigRunner` delegates to a dedicated `P2ImportService`.
4. Standard imports explicitly reject top-level `P2` data, so normal import files and P2 files stay clearly separated.
5. All P2-specific code lives in `de.adorsys.keycloak.config.phasetwo`.

## Why It Was Implemented This Way

The main design goal was isolation.

We wanted to avoid all of the following:

- widening the standard `RealmImport` model with product-specific fields
- mixing P2 semantics into the normal import parser
- creating ambiguous behavior for files that are mostly standard config but also contain P2-specific data

The chosen design therefore treats P2 input as a separate document family with:

- its own provider
- its own validation
- its own service layer
- its own repository / REST integration
- a strict guard that prevents P2 data from being processed by the normal importer

## Current Scope

This is deliberately only an initial slice.

Implemented:

- P2 activation flag
- dedicated P2 document parsing
- validation of `realm` plus `P2.organizations`
- P2 organization synchronization through create, update, and optional delete behavior
- focused tests for branch selection, validation, and organization sync logic

Not implemented:

- a generic extension/plugin framework for external product integrations
- mixed execution of standard imports and P2 imports in one pass
- broader P2 resource coverage beyond the organizations basics slice

## Why This Might Be Reasonable For Core

From our perspective, the strongest arguments in favor are:

1. The standard import model stays untouched.
2. The product-specific logic is physically isolated in its own package subtree.
3. The normal importer is protected from P2 documents by explicit validation.
4. Existing generic infrastructure is reused where appropriate instead of building a second framework.

## Why You Might Not Want This In Core

We also see the main reasons to reject it in its current form:

1. It introduces a second import branch in `KeycloakConfigRunner`.
2. It is clearly tailored to one external product integration.
3. It is not a general extension mechanism, but a concrete feature slice.
4. It adds product awareness to core code, even though the main logic is isolated.

## The Actual Decision We Need From You

The key question is not just whether the code works.

The actual architecture question is:

> Is `keycloak-config-cli` willing to support strongly isolated, opt-in, product-specific import branches in core, as long as they do not alter the standard import schema and are kept behind explicit configuration and validation boundaries?

If the answer is yes, we can continue refining this slice in a way that fits your expectations.

If the answer is no, that is also useful guidance, because then the right direction would be either:

1. keeping this integration out of core entirely, or
2. designing a more general extension mechanism first and only then hanging product-specific integrations off that mechanism.

## Pointers For Review

If you want the detailed technical walkthrough first, the relevant design document is:

- [P2_INC_PLUGIN_HANDOFF.md](https://github.com/telekom-digioss/keycloak-config-cli_p2-inc-extended/blob/v6.5_p2-poc/docs/P2_INC_PLUGIN_HANDOFF.md)

That document describes the exact commit scope, runtime flow, validation boundary, package-level separation, and component logic in more detail.

Any feedback to this topic we would be really happy about.
