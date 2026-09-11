(ns smrops.operation
  "OperationActor -- one compliance-recordkeeping request = one
  supervised actor run, expressed as a langgraph-clj StateGraph. The
  advisor (SmrOpsAdvisor) is sealed into a single node (:advise); its
  proposal is ALWAYS routed through the SmrOperationsGovernor
  (:govern) and the rollout phase gate (:decide) before anything
  commits to the SSoT.

  Everything the actor depends on is injected, so each is a swap, not
  a rewrite:
    - the Store    (MemStore today)              - `store` arg
    - the Advisor  (mock | real LLM)              - :advisor opt
    - the Phase    (0->3 rollout)                 - :phase in ctx

  One graph run = one compliance-recordkeeping request (intake ->
  advise -> govern -> decide -> commit | hold | approval). No
  unbounded inner loop -- each operation is auditable and
  checkpointed.

  Human-in-the-loop = real approval workflow: `interrupt-before
  #{:request-approval}` pauses the actor and hands the decision to a
  human operator. The approver resumes with `{:approval {:status
  :approved}}` (or :rejected).

  ## Why `:commit` independently re-checks `absolute-actuation-request?`

  Every other HARD violation `smrops.governor/check` finds is caught
  exactly ONCE, at `:govern`, and trusted from then on -- a `:hold`
  disposition routes straight to the `:hold` node and structurally
  never reaches `:request-approval`, so no human ever gets a chance to
  override it either way. `absolute-actuation-request-violations` gets
  a SECOND, independent enforcement point here at `:commit`, called
  directly on the proposal immediately before the one node in this
  graph that actually writes the SSoT. This is deliberate belt-and-
  suspenders for the single check in this actor's governor that must
  NEVER, under any circumstance -- including a latent bug in
  `:decide`'s phase-gate wiring, a future refactor that adds a new
  path into `:commit`, or a compromised advisor racing a legitimate
  approval -- allow a live radiological-release/exposure or reactor-
  trip/scram authorization request through. `smrops.governor`'s own
  ns docstring explains why this ONE check, alone among this actor's
  five HARD checks, earns this treatment (its failure mode is not
  about proposal quality/completeness, which a better resubmission
  could fix, but about permanently-excluded intent no resubmission can
  cure)."
  (:require [langgraph.graph :as g]
            [langgraph.checkpoint :as cp]
            [smrops.advisor :as advisor]
            [smrops.governor :as governor]
            [smrops.phase :as phase]
            [smrops.store :as store]))

(defn- commit-fact [request context proposal]
  {:t          :committed
   :op         (:op request)
   :actor      (:actor-id context)
   :site-id    (:site-id request)
   :disposition :commit
   :basis      (:cites proposal)
   :summary    (:summary proposal)})

(defn- commit-record [request _context proposal]
  {:op      (:op proposal)
   :site-id (:site-id request)
   :value   (or (:value proposal) {})
   :payload (:value proposal)})

(defn- absolute-block-fact
  "The audit fact written when the `:commit` node's own redundant
  re-check catches an absolute live-actuation-request proposal --
  distinct `:t` from an ordinary `:governor-hold` so this NEVER-
  reached-in-normal-operation defense-in-depth path is distinguishable
  in the ledger if it is ever exercised."
  [request context]
  {:t          :absolute-actuation-block
   :op         (:op request)
   :actor      (:actor-id context)
   :site-id    (:site-id request)
   :disposition :hold
   :basis      [:absolute-live-actuation-request]
   :absolute?  true})

(defn commit-node
  "The `:commit` node's handler, extracted as a named top-level fn
  (rather than an inline closure) so its defense-in-depth
  `governor/absolute-actuation-request?` re-check (see ns docstring)
  is directly unit-testable WITHOUT needing to defeat `:govern`
  upstream -- a test can hand this fn a `:proposal` carrying absolute-
  actuation-request content directly, alongside whatever `:record`
  an (hypothetically buggy) upstream `:decide` computed, and assert
  this node still refuses to write it. Writes `store` as a side
  effect; returns the same `:commit`/`:hold` result-map shape any
  langgraph-clj node fn returns."
  [store {:keys [request context proposal record]}]
  (if (governor/absolute-actuation-request? proposal)
    (let [f (absolute-block-fact request context)]
      (store/append-ledger! store f)
      {:disposition :hold :audit [f]})
    (do
      (store/commit-record! store record)
      (let [f (commit-fact request context proposal)]
        (store/append-ledger! store f)
        {:audit [f]}))))

(defn build
  "Compiles an OperationActor graph bound to `store` (any `smrops.
  store/Store`).
  opts:
    :advisor      -- a `smrops.advisor/Advisor` (default: mock-advisor)
    :checkpointer -- langgraph checkpointer (default: in-mem)"
  [store & [{:keys [advisor checkpointer]
             :or   {advisor      (advisor/mock-advisor)
                    checkpointer (cp/mem-checkpointer)}}]]
  (-> (g/state-graph
       {:channels
        {:request     {:default nil}
         :context     {:default nil}   ; injected actor-id/role/phase
         :proposal    {:default nil}
         :verdict     {:default nil}
         :disposition {:default nil}   ; :commit | :hold | :escalate
         :record      {:default nil}
         :approval    {:default nil}
         :audit       {:reducer into :default []}}})

      (g/add-node :intake (fn [s] s))

      ;; SmrOpsAdvisor inference (the contained intelligence node) -- proposal only.
      (g/add-node :advise
        (fn [{:keys [request]}]
          (let [p (advisor/-advise advisor store request)]
            {:proposal p :audit [(advisor/trace request p)]})))

      ;; SmrOperationsGovernor -- independent censor (separate system than the advisor).
      (g/add-node :govern
        (fn [{:keys [request context proposal]}]
          {:verdict (governor/check request context proposal store)}))

      ;; Decide: governor disposition, then the rollout-phase gate (which
      ;; can only add caution). HARD governor violations -> HOLD (no override).
      (g/add-node :decide
        (fn [{:keys [request context proposal verdict]}]
          (let [base (phase/verdict->disposition verdict)
                ph   (:phase context phase/default-phase)
                {:keys [disposition reason]} (phase/gate ph request base)]
            (case disposition
              :hold
              {:disposition :hold
               :audit [(cond-> (governor/hold-fact request context verdict)
                         reason (assoc :phase-reason reason :phase ph))]}

              :escalate
              {:disposition :escalate
               :audit [{:t :approval-requested
                        :op (:op request) :site-id (:site-id request)
                        :reason (or reason
                                    (cond (:high-stakes? verdict) :always-escalate
                                          :else :low-confidence))
                        :phase ph
                        :confidence (:confidence verdict)}]}

              :commit
              {:disposition :commit
               :record (commit-record request context proposal)}))))

      ;; Approval handoff -- paused by interrupt-before; a human operator
      ;; resumes with :approval. Then route commit/hold.
      (g/add-node :request-approval
        (fn [{:keys [request context proposal approval verdict]}]
          (if (= :approved (:status approval))
            {:disposition :commit
             :record (assoc (commit-record request context proposal)
                            :payload (assoc (:value proposal)
                                            :approved-by (:by approval)))
             :audit [{:t :approval-granted :op (:op request)
                      :site-id (:site-id request) :by (:by approval)}]}
            {:disposition :hold
             :audit [(merge (governor/hold-fact request context
                                                (assoc verdict :violations
                                                       [{:rule :approver-rejected}]))
                            {:t :approval-rejected})]})))

      ;; Commit -- the ONLY node that writes the SSoT + audit ledger.
      ;; Independently re-derives+re-checks `absolute-actuation-request?`
      ;; on the proposal a SECOND time before writing anything -- see ns
      ;; docstring and `commit-node` for why this specific check gets
      ;; this redundant enforcement point.
      (g/add-node :commit (fn [s] (commit-node store s)))

      ;; Hold -- write the rejection to the ledger; no SSoT mutation.
      (g/add-node :hold
        (fn [{:keys [audit]}]
          (when-let [hf (last (filter #(#{:governor-hold :approval-rejected} (:t %)) audit))]
            (store/append-ledger! store (assoc hf :disposition :hold)))
          {}))

      (g/set-entry-point :intake)
      (g/add-edge :intake :advise)
      (g/add-edge :advise :govern)
      (g/add-edge :govern :decide)

      (g/add-conditional-edges :decide
        (fn [{:keys [disposition]}]
          (case disposition
            :commit   :commit
            :escalate :request-approval
            :hold)))

      (g/add-conditional-edges :request-approval
        (fn [{:keys [disposition]}]
          (if (= :commit disposition) :commit :hold)))

      (g/set-finish-point :commit)
      (g/set-finish-point :hold)

      (g/compile-graph
       {:checkpointer     checkpointer
        :interrupt-before #{:request-approval}})))
