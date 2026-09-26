# Blaze FHIR Server Implementation Guide

This Implementation Guide defines the FHIR conformance artifacts of the [Blaze FHIR Server](https://blaze-server.org) — the profiles, code systems, value sets, search parameters, operation definitions, extensions and naming systems it exposes in its API responses.

The canonical root for all artifacts is `https://blaze-server.org/fhir`.

## Scope

The artifacts in this guide describe:

- **Job profiles** (`Job`, `DiskPerfJob`, `ReIndexJob`, `CompactJob`, `AsyncInteractionJob`) — the Task-based job system that Blaze uses for asynchronous and administrative operations.
- **Aggregation profiles** (`AsyncInteractionRequestBundle`, `AsyncInteractionResponseBundle`) — Bundle profiles used in Blaze's asynchronous interaction request pattern.
- **Code systems** — enumerations used in job parameters, outputs, status reasons, databases and column families.
- **Search parameters** (`TaskInput`, `TaskOutput`) — search parameters for Task resources.
- **Operation definitions** — `$disk-perf` for measuring disk I/O performance.
- **Extensions** — `disk-perf-concurrency` and `validation-outcome`.

## Relationship to the Documentation

The [Blaze documentation site](https://blaze-server.org) describes how to deploy, configure and use the server. This guide provides the machine-readable definitions that Blaze emits at runtime, along with a human-readable summary of each artifact.
