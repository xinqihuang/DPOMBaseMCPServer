## ADDED Requirements

### Requirement: Bounded trace evidence deep dive

The system SHALL provide the read-only `diagnose_trace` tool with a default suspect budget of 5 and an accepted `maxSuspectEvents` range of 1 through 20. The system MUST reject values outside that range as `INVALID_PARAM` before making upstream calls. After fetching topology and trace events, the system SHALL deep-dive selected event details and clobs in deterministic sequence so its own parallel fan-out does not exhaust the shared APM rate limit.

#### Scenario: Default bounded diagnosis
- **WHEN** the caller omits `maxSuspectEvents`
- **THEN** the system SHALL select at most 5 suspect events
- **AND** it SHALL preserve error-first then slowest ordering

#### Scenario: Maximum accepted diagnosis
- **WHEN** the caller supplies `maxSuspectEvents=20`
- **THEN** the system SHALL select at most 20 suspect events
- **AND** detail requests SHALL not be launched as an unbounded concurrent fan-out

#### Scenario: Excessive evidence budget
- **WHEN** the caller supplies `maxSuspectEvents=21`
- **THEN** the system SHALL return `INVALID_PARAM`
- **AND** it MUST NOT make an upstream APM call

### Requirement: Permit-aware APM read calls

The shared APM read limiter SHALL allow a bounded wait for the next permit refresh so a valid bounded trace diagnosis does not fail solely because its own earlier stages consumed the current period's permits. Genuine upstream or timeout failures SHALL remain visible as stage errors.

#### Scenario: Diagnosis crosses one limiter period
- **GIVEN** topology, event and detail calls consume more than one period's permits
- **WHEN** the bounded diagnosis continues
- **THEN** calls SHALL wait within the configured timeout for refreshed permits
- **AND** the system SHALL not report a local `UPSTREAM_THROTTLED` error when a permit becomes available in time
