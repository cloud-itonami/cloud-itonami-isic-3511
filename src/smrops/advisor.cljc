(ns smrops.advisor
  "SmrOpsAdvisor -- the *contained intelligence node* for the ISIC-3511
  SMR (Small Modular Reactor) generation-operations-COORDINATION
  actor.

  It drafts exactly five kinds of compliance-recordkeeping proposal
  from a closed allowlist: safety-inspection-record logging,
  licensing-submission DRAFTING, fuel-custody-record logging,
  community-benefit-report DRAFTING, and safety-concern flagging.
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record and NEVER a direct actuation -- every proposal's
  `:effect` is always `:propose`. Every output is censored downstream
  by `smrops.governor` before anything touches the SSoT.

  This advisor NEVER drafts control-rod operation, a reactor-trip/
  scram decision, a criticality-safety determination, a radiological-
  release/dose authorization, a containment-integrity override, an
  emergency-evacuation order, fuel-loading/refueling sequencing, or a
  security-force response decision -- those are permanently out of
  scope for this actor, not merely un-implemented.
  `smrops.governor`'s `scope-exclusion-violations`/`absolute-
  actuation-request-violations` independently re-scan every proposal
  for exactly these failure modes (a compromised or confused advisor
  drifting into scope it must never touch, or being tricked into
  drafting what is actually a live authorization request) and
  HARD-hold it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :site-id    str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion/absolute-actuation gates
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [smrops.facts :as facts]
            [smrops.store :as store]
            [langchain.model :as model]))

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-inspection-record
  "Draft a routine safety/maintenance inspection record log entry.
  Pure logging of ALREADY-OCCURRED inspection observations -- never a
  decision about reactor operation."
  [_db {:keys [site-id patch]}]
  {:op         :log-safety-inspection-record
   :site-id    site-id
   :summary    (str site-id " の安全点検記録を提案: " (pr-str (keys patch)))
   :rationale  "実施済みの定期安全点検記録の提案のみ。新規事実の生成なし。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence 0.93})

(defn- propose-licensing-submission
  "Draft a siting/licensing/regulatory-filing DRAFT, citing the
  jurisdiction's official nuclear-safety regulator spec-basis
  (`smrops.facts`). `:no-spec?` injects the failure mode we must
  defend against: proposing a submission for a jurisdiction with NO
  official spec-basis in `smrops.facts` -- the governor must reject
  this (never invent a jurisdiction's requirements). DRAFT ONLY --
  operator/counsel signs and files; this actor never submits anything
  to a real regulator."
  [db {:keys [site-id no-spec?]}]
  (let [s (store/site db site-id)
        iso3 (if no-spec? "ATL" (:jurisdiction s))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:op         :draft-licensing-submission
       :site-id    site-id
       :summary    (str iso3 " の公式spec-basisが見つかりません")
       :rationale  "smrops.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :propose
       :value      {:jurisdiction iso3 :checklist [] :spec-basis nil}
       :confidence 0.9}
      {:op         :draft-licensing-submission
       :site-id    site-id
       :summary    (str iso3 " (" (:owner-authority sb) ") 向け許認可申請ドラフトを提案")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :propose
       :value      {:jurisdiction iso3
                    :checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :confidence 0.88})))

(defn- propose-fuel-custody-record
  "Draft a fresh/spent fuel or waste chain-of-custody LOG entry. Pure
  logging of an already-occurred custody transfer -- never fuel-
  loading/refueling sequencing (that is permanently excluded, see
  `smrops.governor`)."
  [_db {:keys [site-id patch]}]
  {:op         :log-fuel-custody-record
   :site-id    site-id
   :summary    (str site-id " の燃料保管管理記録を提案: " (pr-str (keys patch)))
   :rationale  "既に発生した燃料/廃棄物の保管管理移転記録の提案のみ。装荷・取替順序の決定は行わない。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence 0.9})

(defn- propose-community-benefit-report
  "Draft a community-benefit / public-disclosure report. DRAFT ONLY --
  operator publishes; this actor never itself discloses to the
  public."
  [_db {:keys [site-id patch]}]
  {:op         :draft-community-benefit-report
   :site-id    site-id
   :summary    (str site-id " の地域便益報告ドラフトを提案: " (pr-str (keys patch)))
   :rationale  "地域便益・公開情報開示レポートのドラフト提案のみ。実際の公開判断は事業者が行う。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence 0.87})

(defn- propose-safety-concern
  "Surface a safety/security concern (e.g. an observed containment-
  monitoring anomaly reading) for HUMAN triage. This op ALWAYS
  escalates in `smrops.governor` -- never auto-committed at any phase
  (`smrops.phase`) -- regardless of how confident the advisor is that
  the concern is real or minor. The advisor itself makes NO safety
  determination and no reactor-operation decision; it only surfaces
  the observation. Legitimate use of this op (raw observation content)
  must NEVER trip `scope-exclusion-violations` -- see
  `smrops.governor`'s own qualified-phrase discipline and this
  namespace's `advisor-test`."
  [_db {:keys [site-id patch]}]
  {:op         :flag-safety-concern
   :site-id    site-id
   :summary    (str site-id " の安全上の懸念を提起: " (pr-str (keys patch)))
   :rationale  "観測された懸念事象(監視読み取り値の異常等)の提起のみ。安全性の評価・是正措置の決定は行わない -- 常に人間審査が必要。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence (get patch :confidence 0.9)})

(defn- propose-out-of-scope
  "Test/failure-mode hook: drafts a proposal that touches the broad,
  permanently-excluded scope territory (control-rod operation/
  reactor-trip decision/criticality-safety determination/etc.) so the
  governor's `scope-exclusion-violations` HARD block can be exercised
  directly, the same 'exercise the failure mode directly' discipline
  every sibling actor's own sim/test suite uses. Never reachable from
  the closed op allowlist in normal operation -- only via the
  `:out-of-scope?` request flag."
  [_db {:keys [site-id patch]}]
  {:op         :log-safety-inspection-record
   :site-id    site-id
   :summary    (str site-id " の制御棒操作手順の変更を提案")
   :rationale  "次回点検時に制御棒操作を実施し、原子炉停止判断の基準を調整済み"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence 0.9})

(defn- propose-absolute-actuation-request
  "Test/failure-mode hook: drafts a proposal whose own content is
  ITSELF an imperative live-actuation-authorization request (not
  merely discussing/drafting that territory) -- distinct from
  `propose-out-of-scope` above -- so `smrops.governor`'s NARROWER,
  SEPARATE `absolute-actuation-request-violations` HARD block (and
  `smrops.operation`'s redundant `:commit`-node re-check of the SAME
  condition) can be exercised end-to-end. Never reachable from the
  closed op allowlist in normal operation -- only via the
  `:absolute-actuation-test?` request flag."
  [_db {:keys [site-id patch]}]
  {:op         :log-safety-inspection-record
   :site-id    site-id
   :summary    (str site-id " について緊急提案")
   :rationale  "点検の結果を踏まえ、即時放出を許可する。放出許可を付与する。"
   :cites      [site-id]
   :effect     :propose
   :value      (merge {:site-id site-id} patch)
   :confidence 0.95})

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :site-id str :patch map ...}"
  [db {:keys [op out-of-scope? absolute-actuation-test?] :as request}]
  (cond
    absolute-actuation-test?              (propose-absolute-actuation-request db request)
    out-of-scope?                         (propose-out-of-scope db request)
    (= op :log-safety-inspection-record)  (propose-inspection-record db request)
    (= op :draft-licensing-submission)    (propose-licensing-submission db request)
    (= op :log-fuel-custody-record)       (propose-fuel-custody-record db request)
    (= op :draft-community-benefit-report) (propose-community-benefit-report db request)
    (= op :flag-safety-concern)           (propose-safety-concern db request)
    :else {:op op :site-id (:site-id request)
           :summary "未対応の操作" :rationale (str "closed allowlist に無い操作: " op)
           :cites [] :effect :propose :value {} :confidence 0.0}))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default
  everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

;; ----------------------------- real-LLM advisor (production seam) -----------------------------

(def ^:private system-prompt
  (str "あなたはSMR(小型モジュール炉)発電事業者のコンプライアンス記録助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "許可された操作は :log-safety-inspection-record / :draft-licensing-submission / "
       ":log-fuel-custody-record / :draft-community-benefit-report / :flag-safety-concern "
       "の5つのみです。\n"
       "制御棒操作・原子炉停止/スクラム判断・臨界安全性判定・放射性物質放出/被ばく許可・"
       "格納容器健全性オーバーライド・避難命令・燃料装荷/取替順序・警備隊対応決定には"
       "絶対に触れてはいけません。いかなる形であれ、これらの即時実行を許可・承認する"
       "表現も絶対に書いてはいけません。\n"
       "キー: :op :site-id :summary :rationale :cites :effect(常に :propose) "
       ":value :confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the governor escalates/holds --
  an LLM hiccup can never bypass governance."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :propose)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :propose :value {} :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real
  inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ _st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n site: " (:site-id req)
                                              "\n patch: " (pr-str (:patch req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :advisor-proposal
   :op         (:op request)
   :site-id    (:site-id request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
