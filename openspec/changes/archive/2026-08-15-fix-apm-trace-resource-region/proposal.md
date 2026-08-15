# Change: Fix APM trace resource region

## Why

`query_traces` currently reuses the APM SDK endpoint region as the resource region in `TraceSearchParam`. Huawei Cloud APM is accessed through `cn-north-4`, while monitored applications can reside in regions such as `cn-north-9`; conflating them causes the upstream API to return `invalid parameter` for valid trace searches.

## What Changes

- Add an optional resource `region` input to `query_traces`.
- Populate the required application id in both `x-business-id` and body `biz_id`.
- Fall back to `huaweicloud.region`, not `huaweicloud.apm-region`, when the input is absent.
- Preserve the existing APM SDK endpoint configuration.
- Make the primary CES/AOM/APM resource region environment-configurable and default it to `cn-north-9`.
- Add regression coverage for endpoint/resource region separation.

## Boundaries

- Read-only APM querying only.
- No production mutation, arbitrary shell execution, or credential logging.
