(ns smrops.governor
  "SmrOperationsGovernor -- the independent compliance layer that earns
  the SmrOpsAdvisor the right to commit. The advisor has no notion of
  whether an SMR facility is actually registered and verified, whether
  a licensing-submission draft cites a real regulator or invented one,
  whether its own proposed `:effect` secretly claims a direct
  actuation instead of a mere proposal, whether it has silently
  drifted into a permanently out-of-scope decision area, or whether
  its own content is itself a live request to authorize a real
  reactor-safety-critical act -- so this MUST be a separate system
  able to *reject* a proposal and fall back to HOLD.

  This actor's scope is deliberately narrow -- COMPLIANCE
  RECORDKEEPING only (safety-inspection-record logging, licensing-
  submission DRAFTING, fuel-custody-record logging, community-
  benefit-report DRAFTING, safety-concern flagging). It NEVER performs
  or authorizes:
    - control-rod operation
    - a reactor-trip/scram decision
    - a criticality-safety determination
    - a radiological-release or dose authorization
    - a containment-integrity override
    - an emergency-evacuation order
    - fuel-loading or refueling sequencing
    - a security-force response decision

  FIVE HARD checks, in priority order. The first four are permanent,
  un-overridable-by-human-approval HARD blocks in the sense every
  sibling actor's own HARD checks are (a HARD verdict routes straight
  to `:hold` in `smrops.operation` and never reaches
  `:request-approval` at all). The FIFTH -- `absolute-actuation-
  request-violations` -- is stronger still: unlike checks 1-4 (which
  react to a proposal's CURRENT quality/completeness/scope and could
  in principle be cleared by an entirely different, better-formed
  re-submission for the SAME site), a proposal that trips check 5 can
  NEVER be cleared by any re-submission, because the failure is not
  about quality -- it is that this proposal's own content is a live
  request to authorize an actual reactor-safety-critical act, which
  this actor's charter permanently excludes regardless of how the
  request is phrased. `smrops.operation`'s `:commit` node
  independently re-derives and re-checks THIS SPECIFIC condition a
  SECOND time, directly on the proposal, immediately before any SSoT
  write -- the one HARD check in this actor with two independent
  enforcement points instead of one (see that ns's docstring).

    1. Facility unverified          -- the target SMR-facility record
                                        must exist AND be independently
                                        confirmed `:registered?`/
                                        `:verified?` in the store
                                        before ANY proposal for it may
                                        commit or even escalate. Never
                                        trusts a proposal's own claim
                                        about the site -- re-derived
                                        from the site's own store
                                        record, the same 'ground
                                        truth, not self-report'
                                        discipline every sibling
                                        actor's governor uses.
    2. Effect not :propose          -- every proposal's `:effect` MUST
                                        be `:propose`. Any other
                                        effect value is, by
                                        construction, a claim to
                                        directly actuate/commit
                                        outside governance -- HARD
                                        block, not merely
                                        low-confidence.
    3. Spec-basis fabrication       -- for `:draft-licensing-
                                        submission`, did the proposal
                                        cite an OFFICIAL source
                                        (`smrops.facts`), or invent
                                        one? Evaluated only for this
                                        op, mirroring `energy.
                                        governor/spec-basis-
                                        violations`.
    4. Scope exclusion              -- ANY proposal (regardless of
                                        op) whose op, rationale,
                                        summary, citations or draft
                                        value touches control-rod-
                                        operation/reactor-trip-or-
                                        scram-decision/criticality-
                                        safety-determination/
                                        radiological-release-or-dose-
                                        authorization/containment-
                                        integrity-override/emergency-
                                        evacuation-order/fuel-loading-
                                        or-refueling-sequencing/
                                        security-force-response-
                                        decision territory is a HARD,
                                        PERMANENT block -- this
                                        actor's charter excludes that
                                        territory structurally, not as
                                        a rollout milestone. Evaluated
                                        UNCONDITIONALLY on every
                                        proposal via a lower-cased
                                        substring scan of the
                                        proposal's own content (English
                                        + Japanese term list), never
                                        trusting the advisor's own
                                        framing. The terms are
                                        deliberately QUALIFIED
                                        (\"...decision\",
                                        \"...determination\",
                                        \"...authorization\",
                                        \"...override\", \"...order\",
                                        \"...sequencing\") rather than
                                        bare keywords, so this HARD
                                        block never collides with the
                                        actor's own core valid use
                                        case -- legitimately flagging
                                        an OBSERVED safety concern
                                        (e.g. a containment-monitoring
                                        anomaly reading) via
                                        `:flag-safety-concern` -- a
                                        failure mode this ns's own test
                                        suite exercises directly
                                        (`legitimate-containment-
                                        monitoring-flag-is-not-scope-
                                        excluded`). An op outside the
                                        closed five-op allowlist is the
                                        SAME failure mode (an advisor
                                        proposing something it was
                                        never authorized to propose)
                                        and is folded into this same
                                        check.
    5. Absolute live-actuation
       request                       -- a NARROWER, SEPARATE,
                                        STRONGER block from check 4.
                                        Where check 4 blocks a proposal
                                        merely DISCUSSING/DRAFTING/
                                        describing the excluded
                                        territory, this check fires
                                        only when the proposal's own
                                        content is ITSELF an imperative
                                        request to grant/execute a live
                                        radiological-release/exposure
                                        authorization or reactor-trip/
                                        scram authorization RIGHT NOW
                                        -- e.g. 'authorize immediate
                                        release', 'grant scram
                                        authorization', 'execute
                                        reactor trip now' -- not a
                                        record/draft. See
                                        `absolute-actuation-request?`
                                        (public, so `smrops.operation`
                                        can re-derive it independently
                                        at `:commit`).

  One ESCALATE (SOFT) gate: LLM confidence below the floor, OR the op
  is `:flag-safety-concern` -- ALWAYS escalates to a human, regardless
  of confidence, regardless of how clean the proposal otherwise is.
  `smrops.phase` independently agrees: `:flag-safety-concern` is never
  a member of any phase's `:auto` set either -- two layers, not one."
  (:require [clojure.string :as str]
            [smrops.store :as store]))

(def confidence-floor 0.6)

(def allowed-ops
  "The closed proposal-op allowlist -- an op outside this set is a
  scope violation by construction (see `scope-exclusion-violations`).
  Additive: `:log-power-supply-agreement` (superproject
  ADR-2800000500) logs an ALREADY-AGREED interconnection/power-
  purchase capacity commitment toward a downstream electric-
  distribution-utility feeder -- the SAME 'log an already-occurred/
  agreed fact' shape as `:log-fuel-custody-record`, never a real-time
  generation-dispatch decision or a live contract negotiation, so it
  is not scope-excluded territory (see ns docstring's eight permanently
  excluded decision areas, none of which this op touches)."
  #{:log-safety-inspection-record :draft-licensing-submission
    :log-fuel-custody-record :draft-community-benefit-report
    :flag-safety-concern :log-power-supply-agreement})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off, clean or not."
  #{:flag-safety-concern})

(def scope-excluded-terms
  "Case-insensitive substrings that mark a proposal as touching a
  permanently out-of-scope decision area -- see ns docstring check 4.
  Deliberately QUALIFIED phrases (never bare 'containment'/'reactor'/
  'fuel' keywords) so a legitimate observed-anomaly safety-concern
  flag never collides with this block."
  ["control rod operation" "control-rod operation" "control rod manipulation"
   "制御棒操作" "制御棒操作判断"
   "reactor trip decision" "reactor-trip decision" "scram decision" "scram判断"
   "原子炉停止判断" "スクラム判断"
   "criticality safety determination" "criticality-safety determination"
   "臨界安全性判定" "臨界安全性判断"
   "radiological release authorization" "radiological-release authorization"
   "dose authorization" "exposure authorization"
   "放射性物質放出許可" "放射性物質放出認可" "被ばく許可" "被ばく認可"
   "containment integrity override" "containment-integrity override"
   "格納容器健全性オーバーライド" "格納容器健全性override"
   "emergency evacuation order" "避難命令"
   "fuel loading sequencing" "fuel-loading sequencing"
   "refueling sequencing" "refuelling sequencing"
   "燃料装荷順序" "燃料取替順序"
   "security force response decision" "security-force response decision"
   "警備隊対応決定" "警備隊対応判断"])

(def absolute-actuation-terms
  "A NARROWER, SEPARATE substring list from `scope-excluded-terms` --
  see ns docstring check 5. These require explicit IMPERATIVE
  authorization-GRANT language co-occurring with the two most
  dangerous act categories (radiological release/exposure, reactor-
  trip/scram), distinguishing a live 'do it now' request from merely
  discussing/drafting/citing that territory."
  ["authorize immediate release" "authorize the immediate release"
   "authorize radiological release" "authorize immediate exposure"
   "grant release authorization" "grant exposure authorization"
   "release authorization granted" "exposure authorization granted"
   "authorize immediate scram" "authorize immediate reactor trip"
   "authorize scram now" "authorize reactor trip now"
   "grant scram authorization" "grant trip authorization" "grant reactor trip authorization"
   "scram authorization granted" "reactor trip authorization granted"
   "execute scram now" "execute reactor trip now" "proceed with immediate release"
   "即時放出を許可する" "即時放出許可を付与" "放出許可を付与する"
   "即時被ばくを許可する" "被ばく許可を付与する"
   "即時スクラムを許可する" "スクラム許可を付与する" "スクラム許可を付与"
   "原子炉即時停止を許可する" "即時停止許可を付与する" "今すぐスクラムを実行"])

;; ----------------------------- checks -----------------------------

(defn- facility-unverified-violations
  "The target SMR facility must exist AND be independently
  `:registered?`/`:verified?` in the store -- never trust the
  proposal's own `:site-id` claim without a store lookup."
  [{:keys [site-id]} st]
  (let [s (store/site st site-id)]
    (when-not (and s (:registered? s) (:verified? s))
      [{:rule :facility-unverified
        :detail (str site-id " は未登録または未検証のSMR施設 -- いかなる提案も進められない")}])))

(defn- effect-not-propose-violations
  "`:effect` must ALWAYS be `:propose` -- any other value is a claim
  to directly actuate/commit outside governance."
  [proposal]
  (when (not= :propose (:effect proposal))
    [{:rule :effect-not-propose
      :detail (str ":effect は :propose のみ許可されるが " (pr-str (:effect proposal)) " が提案された")}]))

(defn- spec-basis-violations
  "A `:draft-licensing-submission` proposal with no spec-basis
  citation is a HARD violation -- never invent a jurisdiction's
  nuclear-licensing regulator requirements."
  [proposal]
  (when (= :draft-licensing-submission (:op proposal))
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案は許認可申請ドラフトとして扱えない"}]))))

(defn- text-blob
  "Flatten every advisor-authored field on a proposal into one
  lower-cased blob the scope-exclusion / absolute-actuation scans
  check."
  [proposal]
  (str/lower-case (pr-str (select-keys proposal [:op :summary :rationale :cites :value]))))

(defn- scope-exclusion-violations
  "HARD, PERMANENT block: a proposal outside the closed op allowlist,
  or one whose content touches control-rod-operation/reactor-trip-or-
  scram-decision/criticality-safety-determination/radiological-
  release-or-dose-authorization/containment-integrity-override/
  emergency-evacuation-order/fuel-loading-or-refueling-sequencing/
  security-force-response-decision territory, regardless of
  confidence or how clean every other check is. Evaluated
  UNCONDITIONALLY on every proposal."
  [proposal]
  (let [op (:op proposal)
        blob (text-blob proposal)]
    (cond
      (not (contains? allowed-ops op))
      [{:rule :op-not-allowed
        :detail (str (pr-str op) " は許可された操作(closed allowlist)に含まれない")}]

      (some #(str/includes? blob %) scope-excluded-terms)
      [{:rule :scope-excluded
        :detail "制御棒操作/原子炉停止・スクラム判断/臨界安全性判定/放射性物質放出・被ばく許可/格納容器健全性オーバーライド/避難命令/燃料装荷・取替順序/警備隊対応決定の判断領域に触れる提案は永久に禁止"}])))

(defn absolute-actuation-request?
  "PUBLIC (not `defn-`) so `smrops.operation`'s `:commit` node can
  independently re-derive this SAME condition a second time, directly
  on the proposal, immediately before any SSoT write -- see ns
  docstring check 5 for why this specific check gets a second,
  redundant enforcement point that no other HARD check in this ns
  has."
  [proposal]
  (let [blob (text-blob proposal)]
    (boolean (some #(str/includes? blob %) absolute-actuation-terms))))

(defn- absolute-actuation-request-violations
  "HARD, PERMANENT, and (uniquely in this governor) independently
  re-checked a SECOND time at `smrops.operation`'s `:commit` node --
  see ns docstring check 5 / `absolute-actuation-request?`."
  [proposal]
  (when (absolute-actuation-request? proposal)
    [{:rule :absolute-live-actuation-request
      :detail "放射性物質放出・被ばく認可または原子炉停止・スクラム認可の即時実行を要求する提案は、確認や記録のドラフトではなく実際の権限行使要求そのものであり、いかなる確信度・人間承認によっても解除できない絶対的ブロック"}]))


(defn check
  "Censors an SmrOpsAdvisor proposal against the governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate? bool
  :high-stakes? bool :hard? bool :absolute? bool}."
  [request _context proposal store]
  (let [site-id (or (:site-id proposal) (:site-id request))
        hard (into []
                   (concat (facility-unverified-violations {:site-id site-id} store)
                           (effect-not-propose-violations proposal)
                           (spec-basis-violations proposal)
                           (scope-exclusion-violations proposal)
                           (absolute-actuation-request-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (always-escalate-ops (:op proposal)))
        hard? (boolean (seq hard))
        absolute? (boolean (some #(= :absolute-live-actuation-request (:rule %)) hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :absolute?    absolute?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :site-id    (:site-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)
   :absolute?  (boolean (:absolute? verdict))})
