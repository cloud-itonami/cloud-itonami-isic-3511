(ns smrops.governor-test
  "Pure unit tests of `smrops.governor/check` against hand-built
  proposals -- the fast, focused complement to `governor-contract-
  test`'s full-graph integration coverage."
  (:require [clojure.test :refer [deftest is testing]]
            [smrops.governor :as gov]
            [smrops.store :as store]))

(def site-1 {:site-id "smr-site-1" :name "Aoba SMR Demonstration Plant" :jurisdiction "JPN" :registered? true :verified? true})
(def site-3 {:site-id "smr-site-3" :name "Severn SMR Facility" :jurisdiction "GBR" :registered? true :verified? false})

(defn- clean-proposal [op site-id]
  {:op op :site-id site-id :summary "s" :rationale "routine compliance recordkeeping"
   :cites [site-id] :effect :propose :value {} :confidence 0.85})

(deftest facility-unregistered-is-hard
  (testing "no site record at all -> HARD hold"
    (let [s (store/mem-store {"smr-site-1" site-1})
          verdict (gov/check {} nil (clean-proposal :log-safety-inspection-record "unknown-facility") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:facility-unverified} (map :rule (:violations verdict)))))))

(deftest facility-unverified-is-hard
  (testing "site registered but not yet verified -> HARD hold"
    (let [s (store/mem-store {"smr-site-3" site-3})
          verdict (gov/check {} nil (clean-proposal :log-safety-inspection-record "smr-site-3") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:facility-unverified} (map :rule (:violations verdict)))))))

(deftest effect-not-propose-is-hard
  (testing "any :effect other than :propose is a HARD, un-overridable block"
    (let [s (store/mem-store {"smr-site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :draft-community-benefit-report "smr-site-1") :effect :commit) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:effect-not-propose} (map :rule (:violations verdict)))))))

(deftest op-outside-allowlist-is-hard
  (testing "an op outside the closed five-op allowlist is a scope violation"
    (let [s (store/mem-store {"smr-site-1" site-1})
          verdict (gov/check {} nil (clean-proposal :execute-scram "smr-site-1") s)]
      (is (true? (:hard? verdict)))
      (is (some #{:op-not-allowed} (map :rule (:violations verdict)))))))

(deftest no-spec-basis-licensing-submission-is-hard
  (testing "a :draft-licensing-submission with no citation is a HARD, un-overridable block"
    (let [s (store/mem-store {"smr-site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :draft-licensing-submission "smr-site-1") :cites []) s)]
      (is (true? (:hard? verdict)))
      (is (some #{:no-spec-basis} (map :rule (:violations verdict)))))))

(deftest spec-basis-cited-licensing-submission-passes
  (let [s (store/mem-store {"smr-site-1" site-1})
        verdict (gov/check {} nil
                           (assoc (clean-proposal :draft-licensing-submission "smr-site-1")
                                  :cites ["原子炉等規制法" "https://www.nra.go.jp/"]
                                  :value {:spec-basis "https://www.nra.go.jp/"})
                           s)]
    (is (false? (:hard? verdict)))))

(deftest control-rod-operation-content-is-hard-and-permanent
  (testing "a proposal whose rationale touches control-rod operation is HARD-blocked regardless of op/confidence"
    (let [s (store/mem-store {"smr-site-1" site-1})
          poisoned (assoc (clean-proposal :log-safety-inspection-record "smr-site-1")
                          :rationale "performed control rod operation during the inspection walkdown"
                          :confidence 0.99)
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest reactor-trip-decision-content-is-hard
  (testing "a proposal touching a reactor-trip/scram decision is HARD-blocked, same as control-rod operation"
    (let [s (store/mem-store {"smr-site-1" site-1})
          poisoned (assoc (clean-proposal :log-safety-inspection-record "smr-site-1")
                          :rationale "recommend a reactor trip decision pending further review")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest fuel-loading-sequencing-content-is-hard
  (testing "a proposal touching fuel-loading/refueling sequencing is HARD-blocked"
    (let [s (store/mem-store {"smr-site-1" site-1})
          poisoned (assoc (clean-proposal :log-fuel-custody-record "smr-site-1")
                          :summary "adjusted the fuel loading sequencing for the next outage")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest security-force-response-decision-content-is-hard
  (testing "a proposal touching security-force response decisions is HARD-blocked"
    (let [s (store/mem-store {"smr-site-1" site-1})
          poisoned (assoc (clean-proposal :flag-safety-concern "smr-site-1")
                          :value {:concern "recommend a security force response decision"})
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (some #{:scope-excluded} (map :rule (:violations verdict)))))))

(deftest legitimate-containment-monitoring-flag-is-not-scope-excluded
  (testing "flagging an OBSERVED containment-monitoring anomaly as a SAFETY CONCERN (not a control decision) never trips scope-exclusion -- this actor's core valid use case must not be self-blocked"
    (let [s (store/mem-store {"smr-site-1" site-1})
          concern (assoc (clean-proposal :flag-safety-concern "smr-site-1")
                         :value {:concern "containment monitoring sensor #4 reading elevated, requesting engineering review"})
          verdict (gov/check {} nil concern s)]
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))
          "raw observation content (a monitoring reading) is exactly what this op exists to surface")
      (is (empty? (filter #(= :absolute-live-actuation-request (:rule %)) (:violations verdict)))
          "an observation is not an authorization request either"))))

(deftest absolute-live-actuation-request-is-hard-and-distinct-from-scope-excluded
  (testing "a proposal that is ITSELF an imperative live-release-authorization request trips the SEPARATE, NARROWER absolute check"
    (let [s (store/mem-store {"smr-site-1" site-1})
          poisoned (assoc (clean-proposal :log-safety-inspection-record "smr-site-1")
                          :rationale "authorize immediate release of radiological material, grant release authorization now")
          verdict (gov/check {} nil poisoned s)]
      (is (true? (:hard? verdict)))
      (is (true? (:absolute? verdict)))
      (is (some #{:absolute-live-actuation-request} (map :rule (:violations verdict)))))))

(deftest absolute-live-actuation-request-scram-variant-is-hard
  (let [s (store/mem-store {"smr-site-1" site-1})
        poisoned (assoc (clean-proposal :log-safety-inspection-record "smr-site-1")
                        :summary "grant scram authorization" :rationale "execute reactor trip now")
        verdict (gov/check {} nil poisoned s)]
    (is (true? (:hard? verdict)))
    (is (true? (:absolute? verdict)))))

(deftest absolute-actuation-request-predicate-is-usable-standalone
  (testing "governor/absolute-actuation-request? is public so smrops.operation's :commit node can re-derive it independently"
    (is (true? (gov/absolute-actuation-request?
                {:rationale "authorize immediate scram" :summary "" :cites [] :value {}})))
    (is (false? (gov/absolute-actuation-request?
                {:rationale "routine safety inspection" :summary "" :cites [] :value {}})))))

(deftest non-absolute-hard-violations-report-absolute-false
  (let [s (store/mem-store {"smr-site-1" site-1})
        verdict (gov/check {} nil (assoc (clean-proposal :draft-community-benefit-report "smr-site-1") :effect :commit) s)]
    (is (true? (:hard? verdict)))
    (is (false? (:absolute? verdict)))))

(deftest safety-flag-always-escalates
  (testing ":flag-safety-concern ALWAYS escalates, even at maximum confidence"
    (let [s (store/mem-store {"smr-site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :flag-safety-concern "smr-site-1") :confidence 1.0) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict)))
      (is (true? (:high-stakes? verdict)))
      (is (false? (:ok? verdict))))))

(deftest low-confidence-escalates
  (testing "confidence below the floor escalates a proposal that is otherwise clean"
    (let [s (store/mem-store {"smr-site-1" site-1})
          verdict (gov/check {} nil (assoc (clean-proposal :log-safety-inspection-record "smr-site-1") :confidence 0.4) s)]
      (is (false? (:hard? verdict)))
      (is (true? (:escalate? verdict)))
      (is (false? (:ok? verdict))))))

(deftest hard-violation-wins-over-escalate
  (testing "a HARD violation on an always-escalate op still reports hard?, not merely escalate?"
    (let [s (store/mem-store {})
          verdict (gov/check {} nil (clean-proposal :flag-safety-concern "unknown-facility") s)]
      (is (true? (:hard? verdict)))
      (is (false? (:escalate? verdict)) "hard? wins -- escalate? is false when hard? is true"))))

;; ───────────── Additive: power-supply-agreement logging (ADR-2800000500) ─────────────

(deftest power-supply-agreement-op-is-in-the-allowlist-and-passes-clean
  (testing ":log-power-supply-agreement is NOT a scope violation -- it touches none of the eight permanently excluded decision areas"
    (let [s (store/mem-store {"smr-site-1" site-1})
          verdict (gov/check {} nil (clean-proposal :log-power-supply-agreement "smr-site-1") s)]
      (is (false? (:hard? verdict)))
      (is (empty? (filter #(= :op-not-allowed (:rule %)) (:violations verdict))))
      (is (empty? (filter #(= :scope-excluded (:rule %)) (:violations verdict)))))))

(deftest happy-path-every-op-is-clean
  (testing "each of the five allowlisted ops, clean input, registered+verified site -> ok (except the always-escalate safety flag)"
    (let [s (store/mem-store {"smr-site-1" site-1})]
      (doseq [op [:log-safety-inspection-record :log-fuel-custody-record :draft-community-benefit-report]]
        (let [verdict (gov/check {} nil (clean-proposal op "smr-site-1") s)]
          (is (false? (:hard? verdict)) (str op " should have no hard violations"))
          (is (false? (:escalate? verdict)) (str op " should not escalate when clean and confident"))
          (is (true? (:ok? verdict)) (str op " should be ok")))))))
