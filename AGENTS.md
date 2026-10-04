# AGENTS.md — Calypso Agent Standing Rules

This document defines the standing operating rules and constraints for all AI agents and contributors working on the **Calypso** codebase.

---

## Core Standing Rules

### 1. Evidence-Based Verification
- **Rule**: Never claim a feature works unless you have actually run it or an automated test proves it.
- **Requirement**: Always cite concrete `file:line` references, test output, or execution logs as evidence for any verification claim.

### 2. Fact-Checked APIs & Policies
- **Rule**: Never invent, guess, or hallucinate APIs, library versions, Gradle dependencies, or Google Play Console policies.
- **Requirement**: If unsure about an API signature, SDK compatibility, or store policy, say so explicitly and verify against official documentation or source code before proceeding.

### 3. Atomic, Reviewable Commits
- **Rule**: Make small, reviewable commits addressing one concern per change.
- **Requirement**: Do not combine unrelated refactors, dependency updates, and feature code into a single monolithic commit.

### 4. Continuous Build, Test & Lint Validation
- **Rule**: After every functional change, validate the codebase.
- **Requirement**: Build the project, run unit tests, and run lint checks. Report actual terminal outputs, pass/fail counts, and errors rather than assuming success.

### 5. Zero-Leakage Privacy & Sensitive Data Protection
- **Rule**: Never log plaintext message contents, private keys, BIP-39 seed phrases, identity key pairs, or UserCodes in release builds.
- **Requirement**: Ensure all debug logs (`Log.d`, `console.log`) containing cryptographic material or user identifiers are stripped, gated behind `BuildConfig.DEBUG == true`, or eliminated before production compilation.

### 6. Strict Cryptographic & Zero-Telemetry Integrity
- **Rule**: Never weaken cryptographic algorithms/parameters or introduce telemetry, analytics SDKs, advertising libraries, or third-party trackers.
- **Requirement**: Any architectural modification touching the cryptographic pipeline (Signal Protocol, X3DH, SQLCipher, BIP-39 derivation) or introducing external network dependencies requires explicit user authorization beforehand.

### 7. Radical Transparency & Truthfulness
- **Rule**: When something is broken, failing, missing, or deprecated, state it plainly and directly.
- **Requirement**: Never paper over errors, suppress critical warnings, or pretend a broken state is functional.
