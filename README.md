# cloud-itonami-isic-3511

Open Business Blueprint for **ISIC Rev.5 3511**: production of
electricity from non-renewable sources -- narrowed, for this R0, to
the **Small Modular Reactor (SMR) generation-operator compliance-
recordkeeping niche**. An ISIC Wave 1 (governance/professional/energy)
actor per ADR-2607121000, extending that plan's own Top-10 value-
ranking item #6 ("3510/3512 電力") to cover 3511 alongside the sibling
grid-distribution (`cloud-itonami-isic-3510`) and community-renewable-
generation (`cloud-itonami-isic-3512`) actors already in this fleet.

**Maturity: `:implemented`** -- SmrOpsAdvisor ⊣ SmrOperationsGovernor
as a langgraph-clj StateGraph (`intake → advise → govern → decide →
commit/hold`, human-approval interrupt). All source `.cljc` (portable
to JVM / ClojureScript / GraalVM), no JVM-only interop.

## What this actor is -- and is not

This is a **governance/compliance software actor** for an SMR
generation OPERATOR business. It is NOT reactor physics or
engineering software, NOT a control system, and it NEVER performs or
authorizes an actual reactor-safety-critical action. It is an
independent-Governor-gated LLM advisor + append-only audit ledger that
helps an operator draft, log and track regulatory and administrative
records.

## Business-process coverage (honest)

ISIC 3511 (Production of electricity from non-renewable sources) as a
class also covers conventional fossil-fuel (coal/gas/oil) generation.
**This repository's honest R0 scope is the nuclear/SMR operator niche
only** -- it does not cover fossil-generation operations, and does not
claim to (the same narrower-than-the-class framing
`cloud-itonami-isic-3510`'s own ADR uses for its customer/meter-level
distribution slice of the broader 3510 class).

| Covered | Not covered (out of scope for this R0, and permanently for the excluded items) |
|---|---|
| Safety-inspection-record logging (`:log-safety-inspection-record`) | Conventional/fossil (coal/gas/oil) generation operations |
| Licensing-submission DRAFTING, HARD-gated on an official per-jurisdiction spec-basis citation (`:draft-licensing-submission`) -- operator/counsel signs and files, this actor never submits anything to a real regulator | Control-rod operation, PERMANENTLY excluded |
| Fuel-custody-record logging (`:log-fuel-custody-record`) | Reactor-trip/scram decisions, PERMANENTLY excluded |
| Community-benefit-report DRAFTING (`:draft-community-benefit-report`) | Criticality-safety determinations, PERMANENTLY excluded |
| Safety-concern flagging, ALWAYS escalated to a human (`:flag-safety-concern`) | Radiological-release/dose authorization, PERMANENTLY excluded |
| Immutable audit ledger for every proposal/decision | Containment-integrity overrides, emergency-evacuation orders, fuel-loading/refueling sequencing, security-force response decisions -- all PERMANENTLY excluded |

Extending coverage is additive: add the next gate (e.g. an outage-
coordination-log op) as its own governed op with its own HARD checks
and tests, following the SAME "an independent governor re-verifies
against the actor's own records before any real-world act" pattern
this repo's flagship op already establishes.

## CRITICAL: Scope Exclusions

This actor **DOES NOT** and **NEVER WILL**:

- **Control-rod operation**
- **Reactor-trip/scram decisions**
- **Criticality-safety determinations**
- **Radiological-release or dose authorization**
- **Containment-integrity overrides**
- **Emergency-evacuation orders**
- **Fuel-loading or refueling sequencing**
- **Security-force response decisions**

Every proposal the advisor drafts carries `:effect :propose` -- never
a direct actuation -- and `smrops.governor` independently re-scans
every proposal's content for the excluded scope areas above,
regardless of op or confidence (`scope-exclusion-violations`, HARD,
permanent).

### The absolute, stronger block

Separately, and STRONGER than the scope exclusion above: any proposal
that is **itself a request to authorize** an actual radiological-
release/exposure decision or reactor-trip/scram decision -- not
drafting, discussing or logging that territory, but an imperative
"do it now" authorization request -- is blocked **absolutely**
(`absolute-actuation-request-violations`). This check independently
re-derives, from the proposal's own content, that it is a live
actuation request rather than a record/draft, and it is the ONE check
in this actor's governor that is re-checked a SECOND, independent time
-- directly in `smrops.operation`'s `:commit` node, immediately before
any SSoT write -- so no upstream wiring bug, refactor, or race could
ever let it through. See `src/smrops/governor.cljc` and
`src/smrops/operation.cljc` for the full reasoning.

## Operations

Closed proposal-op allowlist (`smrops.governor/allowed-ops`), all
`:effect :propose`:

- `:log-safety-inspection-record` -- routine safety/maintenance inspection record logging
- `:draft-licensing-submission` -- siting/licensing/regulatory-filing DRAFT (NRC/NRA-style submission drafting -- DRAFT ONLY, operator/counsel signs and files)
- `:log-fuel-custody-record` -- fresh/spent fuel or waste chain-of-custody record logging
- `:draft-community-benefit-report` -- community-benefit / public-disclosure report DRAFTING
- `:flag-safety-concern` -- surface an observed safety/security concern -- **ALWAYS escalates**, regardless of confidence

**HARD invariants** (always `:hold`, never human-overridable):

1. **Facility unverified** -- the target SMR-facility record must exist AND be
   independently `:registered?`/`:verified?` in the store before any
   proposal for it may commit or even escalate.
2. **Effect not `:propose`** -- any proposal whose `:effect` is not
   `:propose` is, by construction, a claim to directly actuate outside
   governance.
3. **Spec-basis fabrication** -- a `:draft-licensing-submission` proposal
   that does not cite an official source (`smrops.facts`) for the target
   jurisdiction is rejected -- never invent a jurisdiction's requirements.
4. **Scope exclusion** -- see CRITICAL section above.
5. **Absolute live-actuation request** -- see "The absolute, stronger
   block" above. STRONGER than #4: independently re-checked a second
   time at `smrops.operation`'s `:commit` node.

**ESCALATE** (always human sign-off, when the governor is otherwise clean):

- `:flag-safety-concern` -- always, regardless of confidence.
- Low advisor confidence (`< 0.6`).

## Rollout phases (`smrops.phase`)

Phase 0 (read-only) → 1 (inspection logging, approval-gated) → 2 (adds
licensing-submission drafting, fuel-custody logging and community-
benefit-report drafting, approval-gated) → 3 (supervised auto:
inspection-record/licensing-submission/fuel-custody/community-benefit
drafts may auto-commit when governor-clean and confident).
`:flag-safety-concern` is deliberately absent from every phase's
`:auto` set -- a permanent structural fact, not a rollout milestone
still to come -- matching `smrops.governor`'s own `always-escalate-ops`
independently. No proposal that trips the scope-exclusion or absolute
live-actuation-request checks can EVER auto-commit at any phase either
(a governor HOLD always wins over the phase gate).

## Actuation

**This actor never performs or authorizes an actual reactor-safety-
critical action, at any phase, by construction.** Every record this
actor produces -- a licensing-submission draft, a fuel-custody log
entry, a community-benefit report draft -- carries an **unsigned
certificate** (`"issued_by_registry": false`, `"status": "draft-
unsigned"`, see `smrops.registry`); signature/filing is the operator's
or counsel's own act, never this actor's. Two independent layers
enforce the "never a live actuation" invariant for the actor's op
surface as a whole (`smrops.governor`'s scope-exclusion + absolute-
actuation-request checks, and `smrops.phase`'s phase table, which
never puts `:flag-safety-concern` in any phase's `:auto` set) --
see `smrops.phase`'s docstring and `test/smrops/phase_test.clj`'s
`safety-concern-never-auto-at-any-phase`. The actor may draft, log and
recommend; a human compliance officer or licensed operator always
signs, files or publishes the real record. A THIRD, independent
enforcement point exists specifically for a live radiological-release/
scram authorization request -- see "The absolute, stronger block"
above.

## Run

```bash
clojure -M:run     # walk one clean lifecycle + all HARD-hold cases (including the absolute block) through the actor
clojure -M:test    # governor contract · phase invariants · store parity · registry conformance · facts coverage
clojure -M:lint     # clj-kondo (errors fail; CI mirrors this)
```

## Open business

This repository is not only source code. It is a public, forkable
business model:

| Layer | What is open |
|---|---|
| OSS core | Actor runtime, SmrOperationsGovernor, licensing-submission/fuel-custody draft records, audit ledger |
| Business blueprint | Customer, offer, pricing, unit economics, sales motion |
| Operator playbook | How to fork, license, deploy and support the service in a jurisdiction |
| Trust controls | Governance, security reporting, actuation invariant, audit requirements |

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md) to start this as an
open business on itonami.cloud, and
[`docs/adr/0001-architecture.md`](docs/adr/0001-architecture.md) for the
full architecture and decision record.

## Jurisdiction coverage (honest)

`smrops.facts/coverage` reports how many requested jurisdictions
actually have an official spec-basis in `smrops.facts/catalog` --
currently 3 seeded (JPN/NRA, USA/NRC, GBR/ONR) out of ~194
jurisdictions worldwide. This is a starting catalog to prove the
governor contract end-to-end, not a claim of global coverage. Adding a
jurisdiction is additive: one map entry in `smrops.facts/catalog`,
citing a real official source -- never fabricate a jurisdiction's
requirements to make coverage look bigger.

## Layout

| File | Role |
|---|---|
| `src/smrops/store.cljc` | **Store** protocol -- `MemStore`, string-keyed SMR-facility directory (never a keyword -- ADR-2607152100's bug class avoided from the start) + append-only audit ledger + separate licensing-submission/fuel-custody-record history |
| `src/smrops/registry.cljc` | Licensing-submission-draft + fuel-custody-record construction, unsigned certificates, jurisdiction-scoped sequence numbers |
| `src/smrops/facts.cljc` | Per-jurisdiction SMR-licensing regulatory catalog with an official spec-basis citation per entry, honest coverage reporting |
| `src/smrops/advisor.cljc` | **SmrOpsAdvisor** -- `mock-advisor` ‖ `llm-advisor`; five-op proposal drafting, `:out-of-scope?` and `:absolute-actuation-test?` failure-mode hooks |
| `src/smrops/governor.cljc` | **SmrOperationsGovernor** -- 5 HARD checks (facility-unverified · effect-not-propose · spec-basis fabrication · scope-exclusion · absolute live-actuation request) + 1 soft (confidence/always-escalate gate) |
| `src/smrops/phase.cljc` | **Phase 0→3** -- read-only → assisted inspection → assisted recordkeeping → supervised (four draft/log ops auto-eligible at phase 3; safety-concern flag always human) |
| `src/smrops/operation.cljc` | **OperationActor** -- langgraph-clj StateGraph, with a redundant `:commit`-node re-check of the absolute live-actuation-request condition |
| `src/smrops/sim.cljc` | demo driver |
| `test/smrops/*_test.clj` | governor contract (full-graph) · governor unit tests · advisor · phase invariants · store parity · registry conformance · facts coverage |

## License

Code and implementation templates are AGPL-3.0-or-later.
