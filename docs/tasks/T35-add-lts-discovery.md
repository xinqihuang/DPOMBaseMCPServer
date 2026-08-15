# T35 — Add read-only LTS discovery

## Scope

Expose `list_lts_log_groups` and `list_lts_log_streams` so an agent can obtain real identifiers before calling
`query_lts_logs`. The tools are read-only and use Huawei Cloud SDK 3.1.177.

## Acceptance

- SDK response fields are projected without loss.
- No write API is registered.
- Focused tests and `mvn clean verify` pass.
- Real credentials can discover groups and streams and query the target DPBinMedService logs.
