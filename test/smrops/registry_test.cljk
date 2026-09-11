(ns smrops.registry-test
  (:require [clojure.test :refer [deftest is]]
            [smrops.registry :as registry]))

(deftest register-licensing-submission-draft-builds-numbered-record
  (let [r (registry/register-licensing-submission-draft "smr-site-1" "JPN" 0)]
    (is (= "JPN-LIC-000000" (get r "submission_number")))
    (is (= "licensing-submission-draft" (get-in r ["record" "kind"])))
    (is (= "smr-site-1" (get-in r ["record" "site_id"])))
    (is (true? (get-in r ["record" "immutable"])))
    (is (= "draft-unsigned" (get-in r ["certificate" "status"])))
    (is (false? (get-in r ["certificate" "issued_by_registry"]))
        "certificate is never self-issued -- see README Actuation")))

(deftest register-licensing-submission-draft-sequence-pads-and-increments
  (is (= "USA-LIC-000042" (get (registry/register-licensing-submission-draft "s" "usa" 42) "submission_number")))
  (is (= "GBR-LIC-123456" (get (registry/register-licensing-submission-draft "s" "gbr" 123456) "submission_number"))))

(deftest register-licensing-submission-draft-validates-required-fields
  (is (thrown? Exception (registry/register-licensing-submission-draft "" "JPN" 0)))
  (is (thrown? Exception (registry/register-licensing-submission-draft "s" "" 0)))
  (is (thrown? Exception (registry/register-licensing-submission-draft "s" "JPN" -1))))

(deftest register-fuel-custody-record-builds-numbered-record
  (let [r (registry/register-fuel-custody-record "smr-site-2" "USA" 3)]
    (is (= "USA-FUEL-000003" (get r "custody_number")))
    (is (= "fuel-custody-record" (get-in r ["record" "kind"])))
    (is (= "smr-site-2" (get-in r ["record" "site_id"])))
    (is (false? (get-in r ["certificate" "issued_by_registry"])))))

(deftest register-fuel-custody-record-validates-required-fields
  (is (thrown? Exception (registry/register-fuel-custody-record nil "JPN" 0)))
  (is (thrown? Exception (registry/register-fuel-custody-record "s" nil 0)))
  (is (thrown? Exception (registry/register-fuel-custody-record "s" "JPN" -5))))

(deftest append-conjes-the-record-only
  (let [r (registry/register-fuel-custody-record "s" "JPN" 0)
        h (registry/append [] r)]
    (is (= 1 (count h)))
    (is (= (get r "record") (first h)))))
