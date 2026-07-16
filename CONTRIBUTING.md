# Contributing to cloud-itonami-isic-3511

Contributions should preserve the actor's scope: compliance-recordkeeping
coordination only, with CRITICAL exclusions of control-rod operation,
reactor-trip/scram decisions, criticality-safety determinations,
radiological-release/dose authorization, containment-integrity overrides,
emergency-evacuation orders, fuel-loading/refueling sequencing, and
security-force response decisions (see README.md).

- All code must be .cljc (portable Clojure, no JVM-only constructs).
- Tests must pass: `clojure -M:test`
- Commit messages should link to relevant ADRs or issues.

**This actor does NOT:**
- Control-rod operation, or any reactor-trip/scram decision.
- Criticality-safety determinations.
- Radiological-release or dose authorization.
- Containment-integrity overrides or emergency-evacuation orders.
- Fuel-loading or refueling sequencing.
- Security-force response decisions.
- Anything that is itself a live request to authorize one of the above --
  this is a SEPARATE, stronger, permanently un-overridable block from the
  scope exclusions above (see `src/smrops/governor.cljc`).

Contributions that cross these boundaries will be rejected.
