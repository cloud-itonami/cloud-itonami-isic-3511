(ns smrops.store
  "SSoT for the ISIC-3511 SMR (Small Modular Reactor) generation-
  operations-COORDINATION actor, behind a `Store` protocol so the
  backend is a swap, not a rewrite -- the same seam every
  `cloud-itonami-isic-*` actor in this fleet uses.

  This actor coordinates the BACK OFFICE / compliance-recordkeeping
  surface of an SMR operator: safety-inspection-record logging,
  licensing-submission drafting, fuel-custody-record logging,
  community-benefit-report drafting and safety-concern flagging. It
  NEVER touches control-rod operation, reactor-trip/scram decisions,
  criticality-safety determinations, radiological-release/dose
  authorization, containment-integrity overrides, emergency-
  evacuation orders, fuel-loading/refueling sequencing, or security-
  force response decisions -- see `smrops.governor`'s
  `scope-exclusion-violations` (HARD, permanent) and
  `absolute-actuation-request-violations` (HARD, permanent, and
  independently re-checked a SECOND time at `smrops.operation`'s
  `:commit` node -- see that ns's docstring for why this one gets a
  second, redundant enforcement point).

  `MemStore` -- atom of EDN. The deterministic default for dev/tests/
  demo (no deps). A `sites` directory keyed by `:site-id` STRING
  (never a keyword -- `cloud-itonami-isic-0510`'s own prior scaffold
  attempt keyed the seed map with keyword site-ids while every lookup
  used the string `:site-id` off the proposal, so `(get sites
  site-id)` silently missed on every call and masked itself as HARD
  site-unverified holds across 10 assertions; ADR-2607152100 documents
  the bug class -- avoided here by keying consistently on the string
  from the start, matching `ligniteops.store`'s own fix).

  A registered/verified SMR-facility record must exist before ANY
  proposal for that site may ever commit or escalate --
  `smrops.governor`'s `facility-unverified-violations` re-derives this
  from the site's own `:registered?`/`:verified?` fields, never from
  proposal self-report, the SAME 'ground truth, not self-report'
  discipline every sibling actor's own governor uses.

  The ledger stays append-only: which site a proposal targeted, which
  operation, on what basis, committed/held/escalated and approved by
  whom is always a query over an immutable log."
  (:require [smrops.registry :as registry]))

(defprotocol Store
  (site [s site-id] "Registered SMR-facility record, or nil.
    Site map: {:site-id .. :name .. :jurisdiction .. :registered? bool :verified? bool}.")
  (all-sites [s])
  (ledger [s] "the append-only immutable decision-fact log")
  (coordination-log [s] "the append-only committed generic-proposal history (:log-safety-inspection-record / :draft-community-benefit-report / :flag-safety-concern)")
  (licensing-submission-history [s] "the append-only licensing-submission-draft history (smrops.registry drafts)")
  (fuel-custody-history [s] "the append-only fuel-custody-record history (smrops.registry drafts)")
  (next-licensing-sequence [s jurisdiction] "next licensing-submission sequence for a jurisdiction")
  (next-fuel-custody-sequence [s jurisdiction] "next fuel-custody sequence for a jurisdiction")
  (commit-record! [s record] "apply a committed proposal's record to the SSoT")
  (append-ledger! [s fact] "append one immutable decision fact")
  (with-sites [s sites] "replace/seed the site directory (map site-id->site)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained SMR-facility directory covering both the
  happy path and the governor's own hard checks, so the actor + tests
  run offline."
  []
  {:sites
   {"smr-site-1" {:site-id "smr-site-1" :name "Aoba SMR Demonstration Plant"
                  :jurisdiction "JPN" :registered? true :verified? true}
    "smr-site-2" {:site-id "smr-site-2" :name "Prairie SMR Unit 1"
                  :jurisdiction "USA" :registered? true :verified? true}
    "smr-site-3" {:site-id "smr-site-3" :name "Severn SMR Facility (licensing pending)"
                  :jurisdiction "GBR" :registered? true :verified? false}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- licensing-submission!
  "Backend-agnostic `:draft-licensing-submission` commit -- looks up
  the site via the protocol and drafts the licensing-submission-draft
  record. DRAFT ONLY -- see `smrops.registry`'s own docstring."
  [s site-id]
  (let [st (site s site-id)
        seq-n (next-licensing-sequence s (:jurisdiction st))]
    (registry/register-licensing-submission-draft site-id (:jurisdiction st) seq-n)))

(defn- fuel-custody!
  [s site-id]
  (let [st (site s site-id)
        seq-n (next-fuel-custody-sequence s (:jurisdiction st))]
    (registry/register-fuel-custody-record site-id (:jurisdiction st) seq-n)))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (site [_ site-id] (get-in @a [:sites site-id]))
  (all-sites [_] (sort-by :site-id (vals (:sites @a))))
  (ledger [_] (:ledger @a))
  (coordination-log [_] (:coordination-log @a))
  (licensing-submission-history [_] (:licensing-submissions @a))
  (fuel-custody-history [_] (:fuel-custody-records @a))
  (next-licensing-sequence [_ jurisdiction] (get-in @a [:licensing-sequences jurisdiction] 0))
  (next-fuel-custody-sequence [_ jurisdiction] (get-in @a [:fuel-custody-sequences jurisdiction] 0))
  (commit-record! [s {:keys [op site-id] :as record}]
    (case op
      :draft-licensing-submission
      (let [result (licensing-submission! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:licensing-sequences jurisdiction] (fnil inc 0))
                       (update :licensing-submissions
                               (fn [h] (conj (vec h) (get result "record")))))))
        (swap! a update :coordination-log conj record)
        result)

      :log-fuel-custody-record
      (let [result (fuel-custody! s site-id)
            jurisdiction (:jurisdiction (site s site-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:fuel-custody-sequences jurisdiction] (fnil inc 0))
                       (update :fuel-custody-records
                               (fn [h] (conj (vec h) (get result "record")))))))
        (swap! a update :coordination-log conj record)
        result)

      (do (swap! a update :coordination-log conj record)
          record)))
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-sites [s sites] (when (seq sites) (swap! a assoc :sites sites)) s))

(defn seed-db
  "A MemStore seeded with the demo SMR-facility directory. The
  deterministic default."
  ([] (seed-db (demo-data)))
  ([data]
   (->MemStore
    (atom (merge {:ledger [] :coordination-log []
                  :licensing-sequences {} :licensing-submissions []
                  :fuel-custody-sequences {} :fuel-custody-records []}
                 data)))))

(defn mem-store
  "A MemStore seeded with an explicit `sites` map (site-id string ->
  site map) -- the primary test/dev entry point. `sites` may be empty
  (an unregistered-everywhere store)."
  [sites]
  (seed-db {:sites (or sites {})}))
