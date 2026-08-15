# Change: Harden APM trace diagnosis

## Why

The primitive APM trace tools work with real credentials, but `diagnose_trace` fans out event-detail requests concurrently against the same fail-fast rate limiter. A trace with many suspects therefore produces self-inflicted `UPSTREAM_THROTTLED` gaps and an incomplete evidence bundle.

## What Changes

- Bound a single diagnosis to 1–20 suspect events, default 5.
- Execute detail/clob deep dives sequentially after the initial topology/event fetch.
- Allow the shared APM read limiter to wait briefly for its next permit refresh.
- Preserve partial-result semantics for genuine upstream failures.

## Boundaries

- Read-only APM calls only.
- No retry loop outside the existing resilience layer.
- No automatic production action and no unbounded evidence collection.
