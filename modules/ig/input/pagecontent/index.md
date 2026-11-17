# Blaze FHIR Server Implementation Guide

This Implementation Guide defines the FHIR conformance artifacts of the [Blaze FHIR Server](https://blaze-server.org) — the profiles, code systems, value sets, search parameters, operation definitions, extensions and naming systems it exposes in its API responses.

The canonical root of all artifacts is `https://blaze-server.org/fhir`.

## Scope

The artifacts in this guide describe:

- **Job profiles** (`Job`, `DiskPerfJob`, `ReIndexJob`, `CompactJob`, `AsyncInteractionJob`) — the Task-based job system Blaze uses for asynchronous and administrative operations.
- **Asynchronous interaction profiles** (`AsyncInteractionRequestBundle`, `AsyncInteractionResponseBundle`) — the Bundle profiles of the asynchronous interaction request pattern.
- **Code systems and value sets** — the enumerations used in job parameters, job outputs, status reasons, databases, column families, validation status and access control.
- **Search parameters** (`TaskInput`, `TaskOutput`) — search parameters on Task resources.
- **Operation definitions** — `$disk-perf`, which measures the I/O performance of the disk a database lives on.
- **Extensions** — `disk-perf-concurrency` and `validation-outcome`.
- **Naming systems** — the job number Blaze assigns to every job.
- **Graph definitions** — `PatientPurge`, selecting the resources the Patient `$purge` operation erases together with the patient.

## Relationship to the Documentation

The [Blaze documentation](https://blaze-server.org) describes how to deploy, configure and use the server. This guide carries the machine-readable definitions Blaze refers to at runtime, together with a human-readable rendering of each artifact.
