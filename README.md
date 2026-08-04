# paymenthub-ee-connector-bulk

A Payment Hub EE connector that takes a batch of payment instructions and turns it into individual transfers, then collects the results back together.

[![License](https://img.shields.io/badge/License-MPL--2.0-blue.svg)](LICENSE)

## What it does

- Reads a batch file (CSV) of payment instructions and splits it into single transactions.
- Sends each transaction out on the right payment rail based on its payment mode (for example Mojaloop or a closed-loop channel transfer).
- Runs as a set of Zeebe workers (such as the batch transfer, batch summary, and batch detail workers) driven by a Camunda/Zeebe workflow.
- Uses Apache Camel routes to move data between the workflow, file storage, and the other services.
- Stores batch files and result files in cloud storage (AWS S3 or Azure Blob).
- Reports progress back: it builds per-batch summary counts and a result file, and reports execution status to the bulk-processor.

## How it fits into Payment Hub EE

Payment Hub EE processes bulk payments as a Zeebe/Camunda workflow. This connector is the worker side of that flow. It picks up the batch file, "de-bulks" it into individual transactions, and hands each one to the correct payment connector or channel. It talks to the bulk-processor service to submit the batch and to report execution status, and it correlates the results so the workflow can show a summary and detail view of how the batch went. In short, the bulk-processor manages the batch as a whole, and this connector does the per-transaction work and result gathering underneath it.

## Tech stack

- Java 21
- Spring Boot 3.4
- Apache Camel 4
- Zeebe (Camunda) workers via the Zeebe Java client
- Gradle build
- Depends on `paymenthub-ee-bom` (version management) and `paymenthub-ee-core` (shared connector code)

## Branches

- `dev` is the active development branch — all PRs should target `dev`.
- `main` holds released versions.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and our [Code of Conduct](CODE_OF_CONDUCT.md).
