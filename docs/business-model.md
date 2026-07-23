# Business Model: Small Modular Reactor Generation Operations

## Classification

- Repository: `cloud-itonami-isic-3511`
- ISIC Rev.5: `3511`
- Activity: production of electricity from non-renewable sources (this
  R0's honest scope: the SMR/nuclear generation-operator compliance-
  recordkeeping niche only -- see README `Business-process coverage`)
- Social impact: decarbonization (SMR firm baseload complements
  variable renewables), grid reliability, and a market-entry-
  compliance moat for jurisdictions with heavy licensing burden

## Customer

- SMR generation-operator companies (first-of-a-kind and fleet
  operators)
- utilities piloting or co-owning an SMR project
- regulatory-affairs / licensing counsel teams supporting an operator
- community-relations teams needing auditable public-disclosure
  reporting

## Offer

- safety-inspection-record logging and audit trail
- licensing-submission drafting assistance (per-jurisdiction spec-
  cited checklist, DRAFT ONLY -- operator/counsel signs and files)
- fuel-custody-record logging
- community-benefit / public-disclosure report drafting
- safety-concern intake and escalation workflow
- compliance and audit reporting package

## Revenue

- facility setup / onboarding fee
- monthly managed compliance-recordkeeping operations
- per-jurisdiction licensing-submission drafting package
- compliance and audit reporting package
- support and integration services

## Why SMR specifically (market rationale)

Small Modular Reactors are a fast-growing generation segment (multiple
jurisdictions actively running licensing/design-assessment programs --
see `smrops.facts` for cited regulator sources) with a HEAVY licensing
and compliance burden relative to plant size -- exactly the kind of
market-entry-compliance moat this fleet's own thesis targets (ADR-
2607121000 Top-10 item #6, iso3166×223-country compliance layer
pattern). A governed, audit-ready recordkeeping layer lowers the fixed
cost of standing up compliance operations for each new SMR site/
jurisdiction pair, without this actor ever approaching the reactor-
safety-critical decision surface itself.

## Trust Controls

- every proposal is `:effect :propose` -- never a direct actuation
- a fabricated licensing spec-basis citation, an unregistered/
  unverified facility, or a non-`:propose` effect -- each forces a
  hold, not an override
- control-rod operation, reactor-trip/scram decisions, criticality-
  safety determinations, radiological-release/dose authorization,
  containment-integrity overrides, emergency-evacuation orders, fuel-
  loading/refueling sequencing and security-force response decisions
  are PERMANENTLY excluded, evaluated on every proposal regardless of
  op or confidence
- a proposal that is ITSELF a live radiological-release/scram
  authorization request is blocked ABSOLUTELY, with a second,
  independent enforcement point at commit time
- every recommendation, hold, escalation and approval is logged to an
  immutable audit ledger
- licensing-submission and fuel-custody records carry an UNSIGNED
  certificate -- signature/filing is always the operator's/counsel's
  own act
