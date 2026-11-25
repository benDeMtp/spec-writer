# Specification Quality Checklist: Hiérarchie des tâches et sous-tâches Jira

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-25
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- Les noms d'outils MCP (`next_task`, `start_task`, `get_feature`, `search_knowledge`) et la route
  `/jira/:clé` sont des noms visibles du produit pour l'agent de code et le mainteneur, pas des
  détails d'implémentation.
- SC-005 se vérifie par un essai avec un agent réel, à la main : la consigne est une instruction,
  pas une contrainte imposée par l'outil.
