# Change: Complete APM trace query contract

## Why

`query_traces` already forwards filters and maps failures, but its response omits the current page and whether more results exist. An agent cannot safely continue a bounded investigation from `{total, spans}` alone, especially when a caller selected a non-default page size.

## What Changes

- Return `page`, `pageSize`, and nullable `hasMore` with every trace-search response.
- Compute `hasMore` from upstream `total` without client-side aggregation or sorting.
- Preserve `null` when upstream omits `total`, rather than guessing whether another page exists.
- Lock the response contract with adapter and MCP tests.

## Boundaries

- Read-only APM access only.
- No automatic unbounded pagination.
- No production action, arbitrary shell execution, RAG, or persistence.
