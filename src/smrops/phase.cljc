(ns smrops.phase
  "Phase 0->3 staged rollout for the ISIC-3511 SMR (Small Modular
  Reactor) generation-operations-COORDINATION actor.

    Phase 0  read-only              -- no writes, still governor-gated.
    Phase 1  assisted-inspection    -- safety-inspection-record
                                       logging allowed, every write
                                       needs human approval.
    Phase 2  assisted-recordkeeping -- adds licensing-submission
                                       drafting, fuel-custody-record
                                       logging and community-benefit-
                                       report drafting writes, still
                                       approval.
    Phase 3  supervised auto        -- governor-clean, high-confidence
                                       `:log-safety-inspection-record`/
                                       `:draft-licensing-submission`/
                                       `:log-fuel-custody-record`/
                                       `:draft-community-benefit-
                                       report` may auto-commit (all
                                       DRAFTS/LOGS, never a live
                                       actuation). `:flag-safety-
                                       concern` NEVER auto-commits, at
                                       any phase.

  `:flag-safety-concern` is deliberately ABSENT from every phase's
  `:auto` set, including phase 3 -- a permanent structural fact, not a
  rollout milestone still to come. Surfacing a safety concern always
  needs a human to actually look at it. `smrops.governor`'s own
  `always-escalate-ops` enforces the same invariant independently --
  two layers, not one, agree on this. Likewise, no proposal that trips
  `smrops.governor`'s `scope-exclusion-violations` or
  `absolute-actuation-request-violations` can EVER auto-commit at any
  phase, because both are HARD (governor-hold always wins over the
  phase gate, see `gate` below) -- there is no phase number at which a
  live reactor-safety-critical actuation request becomes eligible for
  this actor to commit."
  (:require [smrops.governor :as governor]))

(def read-ops #{})
(def write-ops governor/allowed-ops)

;; NOTE the invariant: `:flag-safety-concern` is a member of
;; `write-ops` (governor-gated like any write) but is NEVER a member
;; of any phase's `:auto` set below. Do not add it there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops
  allowed to auto-commit when governor-clean>}."
  {0 {:label "read-only"               :writes #{}                                                     :auto #{}}
   1 {:label "assisted-inspection"     :writes #{:log-safety-inspection-record}                        :auto #{}}
   2 {:label "assisted-recordkeeping"  :writes #{:log-safety-inspection-record :draft-licensing-submission
                                                 :log-fuel-custody-record :draft-community-benefit-report} :auto #{}}
   3 {:label "supervised-auto"         :writes write-ops
      :auto #{:log-safety-inspection-record :draft-licensing-submission
              :log-fuel-custody-record :draft-community-benefit-report}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE
    (:phase-approval), even if the governor was clean.
  - `:flag-safety-concern` is never auto-eligible at any phase, so it
    always escalates once the governor clears it (or holds if the
    governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map an SmrOperationsGovernor verdict to a base disposition before
  the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
