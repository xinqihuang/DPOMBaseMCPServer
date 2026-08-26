# Investigation schema releases

Apply `001_investigation_forward.sql` explicitly through the deployment pipeline, then require both queries
in `001_investigation_verify.sql` to return schema version `1`, state `READY`, and `13` required tables.

The application never runs this release automatically in production. The rollback-safe asset deliberately
does not drop durable facts. It changes admission state to `ROLLBACK_REQUESTED` and exposes the aggregate and
pending-publication counts that an operator must reconcile before deploying an older binary.

Release `001` now contains the complete Phase 1B publication-delivery columns for a fresh installation.
For a database provisioned from the earlier Investigation-only form of release `001`, apply
`002_publication_delivery_forward.sql` exactly once. The upgrade freezes canonical content, leasing,
acknowledgement, bounded failure, and operator replay audit metadata without replacing existing intents.
