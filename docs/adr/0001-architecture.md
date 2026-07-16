# ADR-0001: SmrOpsAdvisor ⊣ SmrOperationsGovernor architecture

## Status

Accepted. `cloud-itonami-isic-3511` scaffolded and promoted directly
to `:implemented` in the `kotoba-lang/industry` registry (fresh
scaffold -- no prior `:blueprint`-tier repo existed for this ISIC
class).

## Context

`cloud-itonami-isic-3510` (grid distribution) and
`cloud-itonami-isic-3512` (community renewable generation + storage)
already exist in this fleet, both under ISIC division 35 (electric
power). ISIC Rev.5 group 351 (electric power) splits into 3511
(non-renewable generation) / 3512 (renewable generation) / 3513
(transmission) / 3514 (distribution) / 3515 (trade) / 3516 (storage)
per the Swiss KUBB NOGA-2025/ISIC-Rev.5 coding tool -- 3511 did not yet
have an actor in this fleet. This build adds a Small Modular Reactor
(SMR) nuclear-generation-operator blueprint under 3511, as an
authorized roadmap extension of ADR-2607121000's own Top-10
value-ranking item #6 ("3510/3512 電力"), now covering all three of
3510/3511/3512.

This build follows a smaller, more carefully verified batch discipline
after an earlier same-day 18-agent haiku batch produced a 61% defect
rate (empty implementations, missing modules, false "all tests green"
self-reports) across several `cloud-itonami-isic-*` actors
(`90-docs/adr/2607152300-cloud-itonami-isic-0520-lignite-mining-
coverage.md`). The guardrails from that incident this build follows:
small batch (one actor), verified-redo discipline, and do not trust
self-report -- every test/lint command in this ADR's Verification
Notes was actually run and its literal output pasted, not paraphrased.

**Scope**: this is a governance/compliance software actor for an SMR
generation OPERATOR business -- like every sibling in this fleet, it
is NOT reactor physics/engineering, NOT a control system, and it NEVER
performs or authorizes an actual reactor-safety-critical action. It is
an independent-Governor-gated LLM advisor + append-only audit ledger
that helps an operator draft/log/track regulatory and administrative
records. ISIC 3511 as a class also covers coal/gas/oil generation;
this repo's honest R0 scope is the nuclear/SMR operator niche only --
documented plainly in README `Business-process coverage`, the same
narrower-than-the-class framing `cloud-itonami-isic-3510`'s own ADR
uses for its customer/meter-level distribution slice.

## Decision

### Decision 1: closed five-op proposal allowlist, all `:effect :propose`

Mirroring `cloud-itonami-isic-0520` (ligniteops)'s and
`cloud-itonami-isic-0510` (coalops)'s verified coordination-only
module shape -- the most recently "verified-redo" quality-bar
precedent in this fleet -- rather than `cloud-itonami-isic-3512`
(energy)'s dual-actuation shape, because this actor has NO actuation
event at all (every op is a draft/log, never a real-world act):
`:log-safety-inspection-record`, `:draft-licensing-submission`,
`:log-fuel-custody-record`, `:draft-community-benefit-report`,
`:flag-safety-concern`. An op outside this closed set is treated as
the SAME failure mode as a proposal that drifts into forbidden scope,
not a separate "unknown op" carve-out.

### Decision 2: five HARD checks, the fifth stronger than the rest

1. **Facility unverified** -- the target SMR-facility record must
   exist in the store AND be independently `:registered?`/
   `:verified?` before any proposal for it may commit or even
   escalate. Re-derived from the site's own store record every time.
2. **Effect not `:propose`** -- any proposal whose `:effect` is not
   `:propose` is, by construction, a claim to directly actuate outside
   governance.
3. **Spec-basis fabrication** -- a `:draft-licensing-submission`
   proposal with no citation to an official regulator source
   (`smrops.facts`) is rejected, mirroring
   `cloud-itonami-isic-3512`'s (energy) `spec-basis-violations`.
4. **Scope exclusion** -- any proposal (regardless of op) whose
   content touches control-rod-operation/reactor-trip-or-scram-
   decision/criticality-safety-determination/radiological-release-or-
   dose-authorization/containment-integrity-override/emergency-
   evacuation-order/fuel-loading-or-refueling-sequencing/security-
   force-response-decision territory is a permanent, un-overridable
   block. Evaluated unconditionally via a lower-cased substring scan
   (English + Japanese term list), mirroring `ligniteops.governor`'s
   `scope-exclusion-violations`. Terms are deliberately QUALIFIED
   (e.g. "reactor trip decision", never bare "reactor") so this block
   never collides with the actor's own core valid use case --
   legitimately flagging an OBSERVED anomaly via `:flag-safety-
   concern` -- exercised directly by
   `legitimate-containment-monitoring-flag-is-not-scope-excluded`.
5. **Absolute live-actuation request** -- a NARROWER, SEPARATE,
   STRONGER check. See Decision 3.

### Decision 3: why the absolute check needs its OWN, separate, stronger enforcement

Every other HARD check above reacts to a proposal's CURRENT quality or
completeness -- a fabricated spec-basis citation, an unverified
facility, a non-`:propose` effect, or content that merely discusses
excluded territory -- and in principle a DIFFERENT, better-formed
resubmission could clear it (cite a real source, wait for facility
verification, fix the `:effect` field, remove the offending
discussion). `absolute-actuation-request-violations` is categorically
different: it fires when a proposal's own content is ITSELF an
imperative request to grant/execute a live radiological-release/
exposure or reactor-trip/scram authorization RIGHT NOW ("authorize
immediate release", "grant scram authorization", "execute reactor trip
now") -- not a record, not a draft, not a discussion. No resubmission
can cure this, because the actor's charter permanently excludes
performing or authorizing that act regardless of how the request is
phrased or how well-evidenced it otherwise is.

This ADR's task framing explicitly requires this check to be stronger
than an ordinary HARD check ("no confidence level, no human approval
can clear it") -- but in this fleet's existing StateGraph shape, EVERY
HARD verdict already routes straight to `:hold` and structurally never
reaches `:request-approval`, so no HARD check in any sibling actor is
literally "overridable by human approval" either. The genuinely NEW,
stronger guarantee this build adds is a SECOND, INDEPENDENT
enforcement point: `smrops.operation`'s `:commit` node -- the one node
in the graph that actually writes the SSoT -- independently re-derives
`governor/absolute-actuation-request?` directly from the proposal and
refuses to write if it fires, regardless of what `:record`/
`:disposition` an (hypothetically buggy) upstream `:decide` computed.
This is belt-and-suspenders specifically for the one failure mode this
actor's charter can never tolerate under any circumstance -- including
a latent wiring bug, a future refactor that adds a new path into
`:commit`, or a compromised advisor racing a legitimate approval.
`test/smrops/governor_contract_test.clj`'s
`commit-node-independently-re-blocks-absolute-actuation-content`
exercises this directly by calling `smrops.operation/commit-node` with
a `:record` that would otherwise commit, proving the redundant check
is real, not decorative.

This mirrors, in spirit, why `cloud-itonami-isic-3510`'s own ADR
(90-docs/adr/2607142400) gives `protected-recipient-violations` its
own absolute, un-overridable-by-human-approval treatment distinct from
its other ordinary HARD checks -- both are cases where the task's own
framing is explicit that NO informed-judgment case should ever let the
actor perform the act, unlike an ordinary "insufficient evidence,
please resubmit" HARD hold.

### Decision 4: module shape

`smrops.facts` (official-source citation registry for per-jurisdiction
SMR licensing regulators, no fabrication -- mirroring
`energy.facts`'s honesty discipline), `smrops.registry` (licensing-
submission-draft + fuel-custody-record construction, unsigned
certificates, jurisdiction-scoped sequence numbers -- mirroring
`energy.registry`'s dual-record shape, adapted from actuation records
to DRAFT records since this actor has no actuation), `smrops.store`
(MemStore, STRING-keyed facility directory -- see Decision 5),
`smrops.advisor` (SmrOpsAdvisor: mock + a real-LLM seam via
`langchain.model`, plus TWO distinct test hooks -- `:out-of-scope?`
for the broad scope-exclusion check and `:absolute-actuation-test?`
for the narrower absolute check, deliberately separate so each failure
mode is exercised end-to-end independently), `smrops.governor`
(SmrOperationsGovernor, all checks above, priority-ordered),
`smrops.phase` (0→3 rollout table; `:flag-safety-concern` and the
scope-excluded/absolute-actuation territory are never in any phase's
`:auto` set), `smrops.operation` (a `langgraph-clj` StateGraph: intake
→ advise → govern → decide → commit | hold | request-approval, with
the Decision 3 redundant check at `:commit`), `smrops.sim` (demo
driver, `clojure -M:run`).

### Decision 5: string-keyed site directory, ADR-2607152100's bug class avoided from the start

`smrops.store/MemStore`'s `sites` directory is keyed by `:site-id`
STRING, never a keyword.
`90-docs/adr/2607152100-cloud-itonami-isic-0510-hard-coal-mining-
coverage.md` documents a real bug class in this fleet: an earlier
`cloud-itonami-isic-0510` scaffold attempt keyed its seed map with
KEYWORD site-ids while every governor/advisor lookup used the STRING
`:site-id` off the proposal, so `(get sites site-id)` silently missed
on every call and masked itself as HARD `:site-unverified` holds
across 10 assertions. This build keys consistently on the string from
the start, matching `ligniteops.store`'s own fix, and
`test/smrops/store_contract_test.clj`'s `seed-db-read-parity` /
`mem-store-honors-explicit-sites-map` tests exercise string-keyed
lookups directly.

### Decision 6: no bespoke domain capability lib

This vertical's facility/licensing records are practice-specific
rather than a shared cross-operator data contract, so `smrops.*` runs
on the generic identity/forms/dmn/bpmn/audit-ledger stack only (see
`blueprint.edn` `:itonami.blueprint/required-technologies`) -- the
same posture `cloud-itonami-isic-0520`/`0510` and others without a
bespoke capability lib already establish. `:itonami.blueprint/robotics
false` -- this is a compliance-recordkeeping coordination actor, never
a physical-actuation one, matching `ligniteops`'/`coalops`'s own
`robotics false` framing.

## Alternatives considered

| Option | Verdict | Reason |
|---|---|---|
| Model the absolute live-actuation-request block as just another HARD rule inside `scope-exclusion-violations`, no separate check | ❌ | The task's own framing requires this failure mode to be distinguishably STRONGER and independently re-derived a second time; folding it into the same substring list and the same single enforcement point as the broad scope exclusion would lose both the conceptual distinction (discussing vs. requesting) and the redundant enforcement point this ADR's Decision 3 requires |
| Make the absolute check's re-derivation happen only at `:govern`, relying on `:decide`'s phase-gate wiring to keep it from ever reaching `:commit` | ❌ | This is exactly the single-point-of-failure shape every OTHER HARD check in this actor already has; the task explicitly asks for a check that is stronger than an ordinary HARD check, and a single enforcement point cannot demonstrate that distinction in code, only in a docstring |
| Give this actor a dual-actuation shape like `cloud-itonami-isic-3512` (energy), with `:actuation/dispatch` style ops that always escalate | ❌ | This actor genuinely has no real-world actuation event at all -- every op is a draft or a log entry, not a dispatch. Inventing an actuation op just to match the sibling shape would misrepresent the actor's actual capability surface, contradicting this fleet's honesty discipline (`smrops.facts`'s own docstring) |
| Single `:out-of-scope?` advisor test hook covering both the broad scope-exclusion and the absolute live-actuation-request failure modes | ❌ | The two checks are conceptually and structurally distinct (discussing vs. imperatively requesting); a single hook would not prove each is independently reachable and testable, and would risk one check's phrase list accidentally shadowing coverage of the other |
| `smrops.registry` producing SIGNED certificates for licensing-submission drafts, matching a real regulator's eventual filing format | ❌ | Every record this actor produces is a DRAFT; signing is the operator's/counsel's own act (see README Actuation). A signed certificate from this actor would misrepresent an unfiled draft as an authoritative filing -- `unsigned-certificate`'s `"issued_by_registry": false` / `"status": "draft-unsigned"` fields make this explicit, mirroring `energy.registry`'s own discipline |

## Consequences

- Extends ADR-2607121000's Top-10 value-ranking item #6 to cover all
  three of ISIC 3510/3511/3512 in this fleet.
- Introduces this fleet's first TWO-TIER excluded-territory design in
  one governor: a broad, permanent scope-exclusion check plus a
  narrower, separately-and-redundantly-enforced absolute-actuation-
  request check -- a template other domains with an analogous
  "discussing X is merely excluded, but REQUESTING X live is
  categorically worse" concept may reuse (e.g. a future chemical-
  weapons-adjacent or critical-infrastructure-control actor).
- Confirms the `energy.registry`/`ligniteops.governor` module-shape
  patterns generalize cleanly to a THIRD ISIC-35 vertical with a
  genuinely different actuation profile (zero real actuations, unlike
  `3510`'s/`3512`'s dual-actuation shapes).
- This R0 governs the SMR/nuclear compliance-recordkeeping slice only
  -- no conventional/fossil generation, no reactor-control-system
  integration, no real regulator filing integration; see README
  `Business-process coverage` for the full honest-scope accounting.

## References

- `cloud-itonami-isic-3512/docs/adr/0001-architecture.md` (energy.registry / energy.facts module-shape template)
- `cloud-itonami-isic-0520/docs/adr/` (ADR-2607152300, closed-allowlist / scope-exclusion module-shape template, most recent verified-redo precedent)
- `cloud-itonami-isic-0510/docs/adr/` (ADR-2607152100, string-vs-keyword site-directory bug-class precedent avoided here from the start)
- `90-docs/adr/2607142400-cloud-itonami-isic-3510-electric-power-coverage.md` (`protected-recipient-violations`, this fleet's first always-un-overridable HARD check -- the closest structural precedent for this repo's own absolute check)
- ADR-2607121000 (ISIC/ISCO global reverse-toposort wave plan, Top-10 item #6)
- `kotoba-lang/industry` `resources/kotoba/industry/registry.edn` `"3511"` entry
- Swiss KUBB NOGA-2025/ISIC-Rev.5 coding tool, `https://www.kubb-tool.bfs.admin.ch/en/noga/2025/351` (confirms the 351 group split into 3511-3516)
