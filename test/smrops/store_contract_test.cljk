(ns smrops.store-contract-test
  "The Store contract as executable tests."
  (:require [clojure.test :refer [deftest is testing]]
            [smrops.store :as store]))

(deftest seed-db-read-parity
  (let [s (store/seed-db)]
    (is (= "Aoba SMR Demonstration Plant" (:name (store/site s "smr-site-1"))))
    (is (true? (:registered? (store/site s "smr-site-1"))))
    (is (true? (:verified? (store/site s "smr-site-1"))))
    (is (true? (:registered? (store/site s "smr-site-3"))))
    (is (false? (:verified? (store/site s "smr-site-3"))) "seeded as registered but not yet verified (licensing pending)")
    (is (nil? (store/site s "no-such-facility")))
    (is (= ["smr-site-1" "smr-site-2" "smr-site-3"] (mapv :site-id (store/all-sites s))))
    (is (= [] (store/ledger s)))
    (is (= [] (store/coordination-log s)))
    (is (= [] (store/licensing-submission-history s)))
    (is (= [] (store/fuel-custody-history s)))))

(deftest mem-store-honors-explicit-sites-map
  (let [s (store/mem-store {"a" {:site-id "a" :jurisdiction "JPN" :registered? true :verified? true}})]
    (is (some? (store/site s "a")))
    (is (nil? (store/site s "b"))))
  (testing "an empty sites map means unregistered everywhere"
    (let [s (store/mem-store {})]
      (is (nil? (store/site s "smr-site-1"))))))

(deftest commit-record-generic-op-appends-to-coordination-log
  (let [s (store/seed-db)]
    (store/commit-record! s {:op :log-safety-inspection-record :site-id "smr-site-1" :value {:finding "ok"}})
    (store/commit-record! s {:op :draft-community-benefit-report :site-id "smr-site-1" :value {:period "2026-Q2"}})
    (is (= 2 (count (store/coordination-log s))))
    (is (= [:log-safety-inspection-record :draft-community-benefit-report] (mapv :op (store/coordination-log s))))
    (is (= [] (store/licensing-submission-history s)) "generic ops never write the numbered registries")))

(deftest commit-record-licensing-submission-writes-numbered-registry-and-coordination-log
  (let [s (store/seed-db)]
    (store/commit-record! s {:op :draft-licensing-submission :site-id "smr-site-1" :value {}})
    (is (= 1 (count (store/licensing-submission-history s))))
    (is (= "JPN-LIC-000000" (get (first (store/licensing-submission-history s)) "record_id")))
    (is (= 1 (count (store/coordination-log s))) "also appears in the generic coordination log")
    (store/commit-record! s {:op :draft-licensing-submission :site-id "smr-site-1" :value {}})
    (is (= 2 (count (store/licensing-submission-history s))))
    (is (= "JPN-LIC-000001" (get (second (store/licensing-submission-history s)) "record_id"))
        "sequence increments per jurisdiction")))

(deftest commit-record-fuel-custody-writes-numbered-registry-and-coordination-log
  (let [s (store/seed-db)]
    (store/commit-record! s {:op :log-fuel-custody-record :site-id "smr-site-2" :value {}})
    (is (= 1 (count (store/fuel-custody-history s))))
    (is (= "USA-FUEL-000000" (get (first (store/fuel-custody-history s)) "record_id")))
    (is (= 1 (count (store/coordination-log s))))))

(deftest licensing-and-fuel-sequences-are-independent-per-jurisdiction
  (let [s (store/seed-db)]
    (store/commit-record! s {:op :draft-licensing-submission :site-id "smr-site-1" :value {}}) ; JPN
    (store/commit-record! s {:op :draft-licensing-submission :site-id "smr-site-2" :value {}}) ; USA
    (is (= "JPN-LIC-000000" (get (first (store/licensing-submission-history s)) "record_id")))
    (is (= "USA-LIC-000000" (get (second (store/licensing-submission-history s)) "record_id"))
        "each jurisdiction's own sequence starts independently at 0")))

(deftest ledger-is-append-only-and-order-preserving
  (let [s (store/seed-db)]
    (store/append-ledger! s {:op :a :disposition :commit})
    (store/append-ledger! s {:op :b :disposition :hold})
    (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))

(deftest with-sites-replaces-directory-when-non-empty
  (let [s (store/mem-store {"x" {:site-id "x" :jurisdiction "JPN" :registered? true :verified? true}})]
    (store/with-sites s {"y" {:site-id "y" :jurisdiction "JPN" :registered? true :verified? true}})
    (is (nil? (store/site s "x")))
    (is (some? (store/site s "y"))))
  (testing "an empty replacement is a no-op (never silently wipes the directory)"
    (let [s (store/mem-store {"x" {:site-id "x" :jurisdiction "JPN" :registered? true :verified? true}})]
      (store/with-sites s {})
      (is (some? (store/site s "x"))))))
