# Tasks

- [x] Add `region` to the trace query DTO and MCP tool contract.
- [x] Separate resource-region selection from APM endpoint-region selection.
- [x] Populate required `businessId` in both the request header and body `biz_id`.
- [x] Make the primary resource region environment-configurable with a `cn-north-9` default.
- [x] Keep internal correlator callers backward compatible through fallback.
- [x] Add unit and contract regression tests.
- [x] Run full `mvn clean verify`.
- [x] Restart the local MCP service and verify the real `cn-north-9` query.
