(ns smrops.registry
  "Pure-function jurisdiction-scoped DRAFT record construction for the
  two ops that warrant a formal, sequence-numbered book-of-record
  entry -- `:draft-licensing-submission` and `:log-fuel-custody-
  record` -- mirroring `energy.registry`'s dispatch/settlement-record
  shape.

  Like every sibling actor's registry, there is no single
  international check-digit standard for a licensing-submission-draft
  or fuel-custody-record reference number -- every operator/
  jurisdiction assigns its own reference format. This namespace does
  NOT invent one; it builds a jurisdiction-scoped sequence number and
  validates the record's required fields, the same honest,
  non-fabricating discipline `smrops.facts` uses.

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real reactor-control/plant-instrumentation system. It
  builds the DRAFT RECORD an operator/counsel would review before
  actually filing a licensing submission or logging a fuel-custody
  transfer, never the live act itself (see README `Actuation`: every
  op this actor proposes is `:effect :propose`, and this actor NEVER
  performs or authorizes an actual reactor-safety-critical action --
  see `smrops.governor`'s scope-exclusion and absolute-actuation-
  request checks)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is
  the operator/counsel's own act, not this actor's. See README
  `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn register-licensing-submission-draft
  "Validate + construct the LICENSING-SUBMISSION-DRAFT record -- a
  siting/licensing/regulatory-filing DRAFT only. Pure function -- does
  not touch any real regulator filing system and does not itself file
  anything; it builds the record an operator/counsel would review and
  sign before actually submitting to the jurisdiction's nuclear-safety
  regulator (see `smrops.facts`)."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "licensing-submission: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "licensing-submission: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "licensing-submission: sequence must be >= 0" {})))
  (let [submission-number (str (str/upper-case jurisdiction) "-LIC-" (zero-pad sequence 6))
        record {"record_id" submission-number
                "kind" "licensing-submission-draft"
                "site_id" site-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "submission_number" submission-number
     "certificate" (unsigned-certificate "LicensingSubmissionDraft" submission-number submission-number)}))

(defn register-fuel-custody-record
  "Validate + construct the FUEL-CUSTODY record -- logging (not
  authorizing) a fresh/spent fuel or waste chain-of-custody event.
  Pure function -- does not touch any real fuel-handling/plant-control
  system; it builds the RECORD an operator would keep."
  [site-id jurisdiction sequence]
  (when-not (and site-id (not= site-id ""))
    (throw (ex-info "fuel-custody: site_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "fuel-custody: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "fuel-custody: sequence must be >= 0" {})))
  (let [custody-number (str (str/upper-case jurisdiction) "-FUEL-" (zero-pad sequence 6))
        record {"record_id" custody-number
                "kind" "fuel-custody-record"
                "site_id" site-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "custody_number" custody-number
     "certificate" (unsigned-certificate "FuelCustodyRecord" custody-number custody-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))
