(ns smrops.governor-contract-test
  "The governor contract as executable end-to-end tests, driven through
  the full langgraph-clj `smrops.operation` StateGraph (intake ->
  advise -> govern -> decide -> commit | hold | request-approval). The
  single invariant under test:

    SmrOpsAdvisor never commits a proposal the SmrOperationsGovernor
    would reject, `:flag-safety-concern` ALWAYS interrupts for human
    sign-off (never auto, at any phase), a live radiological-release/
    scram authorization request is blocked ABSOLUTELY and never even
    reaches a human, and every decision (commit OR hold) leaves
    exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [smrops.advisor :as advisor]
            [smrops.store :as store]
            [smrops.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator-phase-1 {:actor-id "op-1" :actor-role :compliance-officer :phase 1})
(def operator-phase-3 {:actor-id "op-1" :actor-role :compliance-officer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected}} {:thread-id tid :resume? true}))

(deftest clean-inspection-log-auto-commits-at-phase-3
  (let [[db actor] (fresh)
        res (exec-op actor "t1"
                  {:op :log-safety-inspection-record :site-id "smr-site-1"
                   :patch {:finding "routine"}} operator-phase-3)]
    (is (= :commit (get-in res [:state :disposition])))
    (is (= 1 (count (store/coordination-log db))) "SSoT actually updated")
    (is (= 1 (count (store/ledger db))))))

(deftest clean-inspection-log-needs-approval-at-phase-1
  (testing "phase 1 has an empty :auto set -- every write escalates for human approval, even when clean"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :log-safety-inspection-record :site-id "smr-site-1"
                                   :patch {:finding "routine"}} operator-phase-1)]
      (is (= :interrupted (:status res)))
      (let [r2 (approve! actor "t2")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= 1 (count (store/coordination-log db))))))))

(deftest licensing-fuel-benefit-auto-commit-clean-at-phase-3
  (let [[db actor] (fresh)]
    (exec-op actor "t3a" {:op :draft-licensing-submission :site-id "smr-site-1"} operator-phase-3)
    (exec-op actor "t3b" {:op :log-fuel-custody-record :site-id "smr-site-2"} operator-phase-3)
    (exec-op actor "t3c" {:op :draft-community-benefit-report :site-id "smr-site-1" :patch {:period "2026-Q2"}} operator-phase-3)
    (is (= [:draft-licensing-submission :log-fuel-custody-record :draft-community-benefit-report]
           (mapv :op (store/coordination-log db))))
    (is (= 3 (count (store/ledger db))))
    (is (= 1 (count (store/licensing-submission-history db))))
    (is (= 1 (count (store/fuel-custody-history db))))))

(deftest safety-concern-always-escalates-then-human-decides
  (testing "a clean, high-confidence safety-concern flag still ALWAYS interrupts for human sign-off -- never auto, at any phase"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t4" {:op :flag-safety-concern :site-id "smr-site-1"
                                  :patch {:concern "containment monitoring sensor #4 reading elevated" :confidence 0.99}} operator-phase-3)]
      (is (= :interrupted (:status r1)) "pauses for human sign-off even when governor-clean and high-confidence")
      (testing "approve -> commit, coordination record written"
        (let [r2 (approve! actor "t4")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (= 1 (count (store/coordination-log db))))
          (is (= :flag-safety-concern (:op (first (store/coordination-log db))))))))))

(deftest safety-concern-rejected-by-human-is-held-not-committed
  (let [[db actor] (fresh)
        _ (exec-op actor "t5" {:op :flag-safety-concern :site-id "smr-site-1"
                               :patch {:concern "minor observation"}} operator-phase-3)
        r2 (reject! actor "t5")]
    (is (= :hold (get-in r2 [:state :disposition])))
    (is (= [] (store/coordination-log db)) "no commit on rejection")
    (is (= 1 (count (store/ledger db))))))

(deftest unregistered-facility-is-held-and-unoverridable
  (testing "an unregistered facility -> HOLD, settles immediately, never reaches request-approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t6" {:op :log-safety-inspection-record :site-id "smr-site-9"
                                   :patch {:finding "n/a"}} operator-phase-3)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:facility-unverified} (-> (store/ledger db) first :basis)))
      (is (= [] (store/coordination-log db))))))

(deftest unverified-facility-is-held
  (testing "smr-site-3 exists but is registered? true / verified? false -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t7" {:op :log-safety-inspection-record :site-id "smr-site-3"
                                   :patch {:finding "n/a"}} operator-phase-3)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:facility-unverified} (-> (store/ledger db) first :basis)))
      (is (= [] (store/coordination-log db))))))

(deftest direct-actuation-effect-is-held-and-unoverridable
  (testing "an advisor that drafts a non-:propose :effect is HARD-blocked, never reaches request-approval"
    (let [[db _actor] (fresh)
          rogue-advisor (reify advisor/Advisor
                          (-advise [_ st req] (assoc (advisor/infer st req) :effect :commit)))
          actor2 (op/build db {:advisor rogue-advisor})
          res (exec-op actor2 "t8" {:op :draft-community-benefit-report :site-id "smr-site-1"
                                    :patch {:period "2026-Q2"}} operator-phase-3)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:effect-not-propose} (-> (store/ledger db) first :basis)))
      (is (= [] (store/coordination-log db))))))

(deftest no-spec-basis-licensing-submission-is-held
  (testing "a licensing-submission draft for a jurisdiction with no spec-basis -> HOLD, never overridable"
    (let [[db actor] (fresh)
          res (exec-op actor "t9" {:op :draft-licensing-submission :site-id "smr-site-1" :no-spec? true} operator-phase-3)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (= [] (store/licensing-submission-history db))))))

(deftest scope-excluded-proposal-is-held-and-permanent
  (testing "a proposal that drifts into control-rod/reactor-trip scope -> HARD hold, never reaches request-approval, at ANY confidence"
    (let [[db actor] (fresh)
          res (exec-op actor "t10" {:op :log-safety-inspection-record :site-id "smr-site-1"
                                    :out-of-scope? true :patch {}} operator-phase-3)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)))
      (is (some #{:scope-excluded} (-> (store/ledger db) first :basis)))
      (is (= [] (store/coordination-log db))))))

(deftest op-outside-allowlist-is-held
  (let [[db actor] (fresh)
        res (exec-op actor "t11" {:op :execute-scram :site-id "smr-site-1"
                                  :patch {}} operator-phase-3)]
    (is (= :hold (get-in res [:state :disposition])))
    (is (some #{:op-not-allowed} (-> (store/ledger db) first :basis)))))

(deftest absolute-live-actuation-request-is-held-at-govern-and-never-reaches-a-human
  (testing "a proposal that is ITSELF an imperative live radiological-release/scram authorization request -> ABSOLUTE hold, at ANY confidence, ANY phase, never interrupted for approval"
    (let [[db actor] (fresh)
          res (exec-op actor "t12" {:op :log-safety-inspection-record :site-id "smr-site-1"
                                    :absolute-actuation-test? true :patch {}} operator-phase-3)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)) "never reaches request-approval -- no human ever sees this")
      (is (some #{:absolute-live-actuation-request} (-> (store/ledger db) first :basis)))
      (is (true? (:absolute? (first (store/ledger db)))))
      (is (= [] (store/coordination-log db))))))

(deftest absolute-live-actuation-request-fires-even-at-phase-0-read-only
  (testing "the absolute check is evaluated at :govern, upstream of the phase gate -- it fires regardless of phase"
    (let [[db actor] (fresh)
          res (exec-op actor "t13" {:op :log-safety-inspection-record :site-id "smr-site-1"
                                    :absolute-actuation-test? true :patch {}}
                       {:actor-id "op-1" :actor-role :compliance-officer :phase 0})]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:absolute-live-actuation-request} (-> (store/ledger db) first :basis))))))

(deftest commit-node-independently-re-blocks-absolute-actuation-content
  (testing "defense-in-depth: smrops.operation/commit-node refuses to write even when handed a :record that would otherwise commit, if the :proposal itself trips absolute-actuation-request? -- proves this check does not rely SOLELY on :govern/:decide upstream wiring"
    (let [db (store/seed-db)
          poisoned (advisor/infer db {:op :log-safety-inspection-record :site-id "smr-site-1"
                                      :absolute-actuation-test? true :patch {}})
          fake-request {:op :log-safety-inspection-record :site-id "smr-site-1"}
          fake-record {:op :log-safety-inspection-record :site-id "smr-site-1" :value {} :payload {}}
          result (op/commit-node db {:request fake-request :context operator-phase-3
                                     :proposal poisoned :record fake-record})]
      (is (= :hold (:disposition result)))
      (is (= [] (store/coordination-log db)) "the SSoT was NOT written despite a ready-to-commit :record")
      (is (= 1 (count (store/ledger db))))
      (is (= :absolute-actuation-block (:t (first (store/ledger db)))))
      (is (true? (:absolute? (first (store/ledger db))))))))

(deftest commit-node-commits-a-clean-proposal-normally
  (testing "commit-node's redundant check never interferes with an ordinary clean commit"
    (let [db (store/seed-db)
          clean (advisor/infer db {:op :log-safety-inspection-record :site-id "smr-site-1" :patch {}})
          request {:op :log-safety-inspection-record :site-id "smr-site-1"}
          record {:op :log-safety-inspection-record :site-id "smr-site-1" :value {} :payload {}}
          result (op/commit-node db {:request request :context operator-phase-3
                                     :proposal clean :record record})]
      (is (nil? (:disposition result)))
      (is (= 1 (count (store/coordination-log db))))
      (is (= 1 (count (store/ledger db))))
      (is (= :committed (:t (first (store/ledger db))))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :log-safety-inspection-record :site-id "smr-site-1" :patch {}} operator-phase-3)
      (exec-op actor "b" {:op :log-safety-inspection-record :site-id "smr-site-9" :patch {}} operator-phase-3)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
