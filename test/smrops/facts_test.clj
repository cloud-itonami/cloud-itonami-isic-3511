(ns smrops.facts-test
  (:require [clojure.test :refer [deftest is]]
            [smrops.facts :as facts]))

(deftest spec-basis-returns-nil-for-unlisted-jurisdiction
  (is (nil? (facts/spec-basis "ATL")))
  (is (nil? (facts/spec-basis "XYZ"))))

(deftest spec-basis-returns-citation-map-for-seeded-jurisdictions
  (doseq [iso3 ["JPN" "USA" "GBR"]]
    (let [sb (facts/spec-basis iso3)]
      (is (some? sb) (str iso3 " should be seeded"))
      (is (string? (:owner-authority sb)))
      (is (string? (:legal-basis sb)))
      (is (string? (:provenance sb)) "must cite a real official source URL")
      (is (seq (:required-evidence sb))))))

(deftest coverage-is-honest
  (let [c (facts/coverage ["JPN" "USA" "GBR" "ATL" "XYZ"])]
    (is (= 5 (:requested c)))
    (is (= 3 (:covered c)))
    (is (= ["GBR" "JPN" "USA"] (:covered-jurisdictions c)))
    (is (= ["ATL" "XYZ"] (:missing-jurisdictions c)))
    (is (string? (:note c)))))

(deftest coverage-default-reports-full-catalog
  (let [c (facts/coverage)]
    (is (= (count facts/catalog) (:covered c)))
    (is (= (:requested c) (:covered c)))
    (is (empty? (:missing-jurisdictions c)))))

(deftest evidence-checklist-empty-for-unlisted-jurisdiction
  (is (= [] (facts/evidence-checklist "ATL"))))

(deftest evidence-checklist-non-empty-for-seeded-jurisdiction
  (is (seq (facts/evidence-checklist "JPN"))))
