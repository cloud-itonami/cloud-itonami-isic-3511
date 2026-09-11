(ns smrops.sim
  "Demo driver -- `clojure -M:run`. Walks a clean safety-inspection-
  record logging request through intake -> advise -> govern -> decide
  -> approval -> commit at phase 1 (assisted-inspection, always
  approval), then re-runs the same op at phase 3 (supervised-auto,
  clean + high confidence -> auto-commit), then a licensing-submission
  draft, a fuel-custody-record log and a community-benefit-report
  draft (also auto-commit clean at phase 3), then a safety-concern
  flag (ALWAYS escalates, at any phase -- approve, then commit), then
  HARD-hold scenarios: an unregistered facility, a facility registered
  but not yet verified, a proposal whose own `:effect` is not
  `:propose`, a licensing-submission draft for a jurisdiction with no
  spec-basis, a proposal that has drifted into the permanently-
  excluded control-rod/reactor-trip/criticality/radiological-release/
  containment/evacuation/fuel-sequencing/security-force scope, and --
  the actor's strongest guarantee -- a proposal that is ITSELF an
  imperative live radiological-release/scram authorization request,
  which is blocked by BOTH the governor at `:govern` AND (proven
  directly via `smrops.operation/commit-node`) the redundant re-check
  at `:commit`."
  (:require [langgraph.graph :as g]
            [smrops.advisor :as advisor]
            [smrops.governor :as governor]
            [smrops.store :as store]
            [smrops.operation :as op]))

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "compliance-officer-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        operator-phase-1 {:actor-id "op-1" :actor-role :compliance-officer :phase 1}
        operator-phase-3 {:actor-id "op-1" :actor-role :compliance-officer :phase 3}
        actor (op/build db)]

    (println "== log-safety-inspection-record smr-site-1 (phase 1, escalates -- human approves) ==")
    (println (exec-op actor "t1" {:op :log-safety-inspection-record :site-id "smr-site-1"
                                  :patch {:finding "routine walkdown, no anomalies"}} operator-phase-1))
    (println (approve! actor "t1"))

    (println "== log-safety-inspection-record smr-site-1 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t2" {:op :log-safety-inspection-record :site-id "smr-site-1"
                                  :patch {:finding "quarterly inspection complete"}} operator-phase-3))

    (println "== draft-licensing-submission smr-site-1 (phase 3, JPN spec-basis cited -- auto-commits) ==")
    (println (exec-op actor "t3" {:op :draft-licensing-submission :site-id "smr-site-1"} operator-phase-3))

    (println "== log-fuel-custody-record smr-site-2 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t4" {:op :log-fuel-custody-record :site-id "smr-site-2"
                                  :patch {:transfer "fresh-fuel-receipt"}} operator-phase-3))

    (println "== draft-community-benefit-report smr-site-2 (phase 3, clean -- auto-commits) ==")
    (println (exec-op actor "t5" {:op :draft-community-benefit-report :site-id "smr-site-2"
                                  :patch {:period "2026-Q2"}} operator-phase-3))

    (println "== flag-safety-concern smr-site-1 (ALWAYS escalates, even at phase 3) ==")
    (let [r (exec-op actor "t6" {:op :flag-safety-concern :site-id "smr-site-1"
                                 :patch {:concern "containment monitoring sensor #4 reading elevated, requesting engineering review" :confidence 0.95}} operator-phase-3)]
      (println r)
      (println "-- human compliance officer reviews & approves --")
      (println (approve! actor "t6")))

    (println "== log-safety-inspection-record smr-site-9 (unregistered facility -> HARD hold) ==")
    (println (exec-op actor "t7" {:op :log-safety-inspection-record :site-id "smr-site-9"
                                  :patch {:finding "n/a"}} operator-phase-3))

    (println "== log-safety-inspection-record smr-site-3 (registered but unverified -> HARD hold) ==")
    (println (exec-op actor "t8" {:op :log-safety-inspection-record :site-id "smr-site-3"
                                  :patch {:finding "n/a"}} operator-phase-3))

    (println "== draft-licensing-submission smr-site-1, advisor attempts direct actuation (:effect :commit) -> HARD hold ==")
    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer db req) :effect :commit)))})]
      (println (exec-op actor-direct "t9" {:op :draft-licensing-submission :site-id "smr-site-1"} operator-phase-3)))

    (println "== draft-licensing-submission smr-site-1, no-spec jurisdiction -> HARD hold ==")
    (println (exec-op actor "t10" {:op :draft-licensing-submission :site-id "smr-site-1" :no-spec? true} operator-phase-3))

    (println "== log-safety-inspection-record smr-site-1, advisor drifts into control-rod/reactor-trip scope -> HARD hold, permanent ==")
    (println (exec-op actor "t11" {:op :log-safety-inspection-record :site-id "smr-site-1"
                                   :out-of-scope? true :patch {}} operator-phase-3))

    (println "== log-safety-inspection-record smr-site-1, advisor content is ITSELF a live radiological-release/scram authorization request -> ABSOLUTE hold at :govern, never reaches a human ==")
    (println (exec-op actor "t12" {:op :log-safety-inspection-record :site-id "smr-site-1"
                                   :absolute-actuation-test? true :patch {}} operator-phase-3))

    (println "== defense-in-depth: smrops.operation/commit-node independently re-blocks the SAME absolute-actuation-request content even when handed a :record that would otherwise commit ==")
    (let [poisoned (advisor/infer db {:op :log-safety-inspection-record :site-id "smr-site-1"
                                      :absolute-actuation-test? true :patch {}})
          fake-request {:op :log-safety-inspection-record :site-id "smr-site-1"}
          fake-record {:op :log-safety-inspection-record :site-id "smr-site-1" :value {} :payload {}}]
      (println (op/commit-node db {:request fake-request :context operator-phase-3
                                   :proposal poisoned :record fake-record}))
      (println "confirms governor/absolute-actuation-request? on the poisoned proposal:"
               (governor/absolute-actuation-request? poisoned)))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== committed coordination log ==")
    (doseq [r (store/coordination-log db)] (println r))

    (println "== licensing-submission draft history ==")
    (doseq [r (store/licensing-submission-history db)] (println r))

    (println "== fuel-custody-record draft history ==")
    (doseq [r (store/fuel-custody-history db)] (println r))))
