# Operator Guide

## First Deployment

1. Register one SMR facility (`:registered? true`) after independent
   verification (`:verified? true`) -- the governor will HARD-hold any
   proposal for a facility that has not cleared both.
2. Import historical safety-inspection and fuel-custody records.
3. Run the advisor in read-only mode (phase 0).
4. Compare recommendations with current compliance-recordkeeping
   practice.
5. Enable human-approved inspection-record logging (phase 1), then
   licensing-submission drafting / fuel-custody logging / community-
   benefit-report drafting (phase 2), then supervised auto-commit for
   those four draft/log ops once trust is established (phase 3).
   `:flag-safety-concern` always requires human sign-off, at every
   phase.

## Minimum Production Controls

- customer-owned facility registration/verification records
- clear licensing spec-basis source per jurisdiction (`smrops.facts`)
- approval workflow for every write at phase 1-2, and for the safety-
  concern flag at every phase
- incident contact for facility compliance officers
- monthly audit export

## What this actor will never do for you

This actor will never draft, discuss, or authorize control-rod
operation, a reactor-trip/scram decision, a criticality-safety
determination, a radiological-release/dose authorization, a
containment-integrity override, an emergency-evacuation order, fuel-
loading/refueling sequencing, or a security-force response decision.
An operator that needs software support for those decisions needs a
licensed reactor-protection/control system, not this actor -- this
actor is compliance-recordkeeping software only.

## Certification

Certified operators must prove facility registration/verification
provenance, licensing-submission traceability (DRAFT ONLY -- the
operator/counsel signs and files), and that automated actions cannot
bypass the SmrOperationsGovernor.
