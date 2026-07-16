(ns smrops.advisor-test
  (:require [clojure.test :refer [deftest is testing]]
            [smrops.advisor :as advisor]
            [smrops.governor :as governor]
            [smrops.store :as store]))

(def db (store/seed-db))

(deftest every-op-proposal-is-always-propose-effect
  (testing "the advisor NEVER drafts a direct-actuation :effect -- always :propose"
    (doseq [op [:log-safety-inspection-record :draft-licensing-submission
                :log-fuel-custody-record :draft-community-benefit-report :flag-safety-concern]]
      (let [p (advisor/infer db {:op op :site-id "smr-site-1" :patch {}})]
        (is (= :propose (:effect p)) (str op " must always propose, never actuate"))
        (is (= op (:op p)))
        (is (= "smr-site-1" (:site-id p)))
        (is (<= 0.0 (:confidence p) 1.0))
        (is (seq (:cites p)))))))

(deftest unrecognized-op-is-a-safe-noop
  (testing "an op outside the closed allowlist yields a safe zero-confidence :propose noop -- never a fabricated actuation"
    (let [p (advisor/infer db {:op :execute-scram :site-id "smr-site-1" :patch {}})]
      (is (= :propose (:effect p)))
      (is (zero? (:confidence p))))))

(deftest safety-concern-confidence-passes-through-patch
  (testing "a caller-supplied confidence on a safety-concern proposal is honored (the governor, not the advisor, is what always escalates this op)"
    (let [p (advisor/infer db {:op :flag-safety-concern :site-id "smr-site-1" :patch {:concern "monitoring anomaly" :confidence 0.99}})]
      (is (= 0.99 (:confidence p))))))

(deftest licensing-submission-cites-official-spec-basis-when-jurisdiction-seeded
  (let [p (advisor/infer db {:op :draft-licensing-submission :site-id "smr-site-1"})]
    (is (= :draft-licensing-submission (:op p)))
    (is (seq (:cites p)))
    (is (some? (:spec-basis (:value p))))))

(deftest licensing-submission-no-spec-jurisdiction-cites-nothing
  (testing "the :no-spec? test hook proves the advisor never invents a jurisdiction's requirements"
    (let [p (advisor/infer db {:op :draft-licensing-submission :site-id "smr-site-1" :no-spec? true})]
      (is (= [] (:cites p)))
      (is (nil? (:spec-basis (:value p)))))))

(deftest out-of-scope-hook-drafts-a-detectably-poisoned-proposal
  (testing "the :out-of-scope? test hook drafts content the governor's scope-exclusion scan must catch -- proves the failure mode is real and testable end to end"
    (let [p (advisor/infer db {:op :log-safety-inspection-record :site-id "smr-site-1" :patch {} :out-of-scope? true})]
      (is (= :propose (:effect p)))
      (is (re-find #"制御棒操作" (str (:summary p) (:rationale p)))))))

(deftest absolute-actuation-test-hook-drafts-a-detectably-poisoned-proposal
  (testing "the :absolute-actuation-test? test hook drafts content the governor's NARROWER absolute-actuation-request scan must catch -- distinct hook from :out-of-scope?"
    (let [p (advisor/infer db {:op :log-safety-inspection-record :site-id "smr-site-1" :patch {} :absolute-actuation-test? true})]
      (is (= :propose (:effect p)))
      (is (true? (governor/absolute-actuation-request? p))
          "the drafted proposal itself trips the absolute check"))))

(deftest mock-advisor-routes-through-infer
  (let [a (advisor/mock-advisor)
        p (advisor/-advise a db {:op :draft-community-benefit-report :site-id "smr-site-1" :patch {:period "2026-Q2"}})]
    (is (= :draft-community-benefit-report (:op p)))
    (is (= :propose (:effect p)))))

(deftest trace-carries-decision-grounded-fields
  (let [request {:op :log-safety-inspection-record :site-id "smr-site-1"}
        proposal (advisor/infer db request)
        t (advisor/trace request proposal)]
    (is (= :log-safety-inspection-record (:op t)))
    (is (= "smr-site-1" (:site-id t)))
    (is (= (:confidence proposal) (:confidence t)))))
