(ns smrops.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:flag-safety-concern` must NEVER be a member of any
  phase's `:auto` set, and no other actual-actuation-adjacent op
  exists in `smrops.governor/allowed-ops` at all (the closed
  five-op allowlist is entirely draft/log ops)."
  (:require [clojure.test :refer [deftest is testing]]
            [smrops.governor :as governor]
            [smrops.phase :as phase]))

(deftest safety-concern-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in future entries, auto-commits a safety-concern flag"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :flag-safety-concern))
          (str "phase " n " must not auto-commit :flag-safety-concern")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest inspection-logging-enabled-from-phase-1
  (is (contains? (:writes (get phase/phases 1)) :log-safety-inspection-record))
  (is (not (contains? (:writes (get phase/phases 0)) :log-safety-inspection-record))))

(deftest licensing-fuel-benefit-enabled-from-phase-2
  (doseq [op [:draft-licensing-submission :log-fuel-custody-record :draft-community-benefit-report]]
    (is (contains? (:writes (get phase/phases 2)) op))
    (is (not (contains? (:writes (get phase/phases 1)) op)))))

(deftest safety-concern-enabled-only-from-phase-3
  (is (contains? (:writes (get phase/phases 3)) :flag-safety-concern))
  (is (not (contains? (:writes (get phase/phases 2)) :flag-safety-concern))))

(deftest phase-3-auto-commits-four-of-five-ops
  (testing ":flag-safety-concern is the only non-auto-eligible op at phase 3 -- always human sign-off"
    (is (= #{:log-safety-inspection-record :draft-licensing-submission
             :log-fuel-custody-record :draft-community-benefit-report}
           (:auto (get phase/phases 3))))))

(deftest write-ops-is-exactly-the-governor-allowlist
  (is (= governor/allowed-ops phase/write-ops)))

;; ───────────── Additive: power-supply-agreement logging (ADR-2800000500) ─────────────

(deftest power-supply-agreement-enabled-from-phase-2
  (is (contains? (:writes (get phase/phases 2)) :log-power-supply-agreement))
  (is (not (contains? (:writes (get phase/phases 1)) :log-power-supply-agreement))))

(deftest power-supply-agreement-never-auto-at-any-phase
  (testing "deliberately absent from every phase's :auto set, including phase 3 -- see ns docstring"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :log-power-supply-agreement))
          (str "phase " n " must not auto-commit :log-power-supply-agreement")))))

(deftest phase-3-auto-set-is-unchanged-by-power-supply-agreement-addition
  (is (= #{:log-safety-inspection-record :draft-licensing-submission
           :log-fuel-custody-record :draft-community-benefit-report}
         (:auto (get phase/phases 3)))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :log-safety-inspection-record} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :flag-safety-concern} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :log-safety-inspection-record} :commit))))
  (is (= :phase-disabled (:reason (phase/gate 0 {:op :log-safety-inspection-record} :commit)))))

(deftest gate-auto-commits-a-clean-auto-eligible-write-at-phase-3
  (is (= :commit (:disposition (phase/gate 3 {:op :log-safety-inspection-record} :commit)))))

(deftest verdict->disposition-priority
  (is (= :hold (phase/verdict->disposition {:hard? true :escalate? false})))
  (is (= :escalate (phase/verdict->disposition {:hard? false :escalate? true})))
  (is (= :commit (phase/verdict->disposition {:hard? false :escalate? false}))))
