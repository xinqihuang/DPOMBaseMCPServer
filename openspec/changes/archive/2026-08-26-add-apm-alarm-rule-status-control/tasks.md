## 1. Adapter Contract

- [x] 1.1 Add request/response DTOs for rule ID, target enabled state, upstream success marker and operation result.
- [x] 1.2 Add `ApmAlarmRuleAdminAdapter` with Chinese Javadoc that explicitly documents whole-rule impact and no automatic retry.

## 2. Authenticated REST Transport

- [x] 2.1 Add injectable APM rule-admin transport/configuration that reuses `HuaweiCloudProperties`, SDK core HTTP timeouts and the existing AK/SK credential chain without logging secrets.
- [x] 2.2 Build and authenticate `PUT /v2/alarm-center/rule/update-rule-disable` with encoded `alarm_rule_id` and `enable` query parameters and a region-derived, non-hardcoded endpoint.
- [x] 2.3 Parse only the documented `{ "ok": "ok" }` success contract and map HTTP/connection/timeout failures to the common error model with `X-Request-Id` propagation.

## 3. Verification

- [x] 3.1 Add unit tests for enable/disable request construction, positive-ID/non-null validation, strict success parsing and absence of upstream calls on invalid input.
- [x] 3.2 Add transport/error tests covering signed authentication, 400, 401/403, 429, 5xx, timeout and request-trace propagation without exposing credentials.
- [x] 3.3 Run focused APM adapter tests, the repository verification suite and strict OpenSpec validation; confirm no MCP tool or startup side effect was introduced.
