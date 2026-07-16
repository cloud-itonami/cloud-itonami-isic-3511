(ns smrops.facts
  "Per-jurisdiction SMR (Small Modular Reactor) siting/licensing
  regulatory catalog -- the G2-style spec-basis table the SMR
  Operations Governor checks every `:draft-licensing-submission`
  proposal against ('did the advisor cite an OFFICIAL public source for
  this jurisdiction's reactor-licensing authority, or did it invent
  one?').

  Coverage is reported HONESTLY (see `coverage`), the same discipline
  every sibling actor's `facts` namespace uses (`energy.facts`,
  `grid.facts`, ...): a jurisdiction not in this table has NO
  spec-basis, full stop -- the advisor must not fabricate one, and the
  governor holds if it tries.

  Seed values are drawn from each jurisdiction's official nuclear-
  safety regulator (see `:provenance`); they are a STARTING catalog,
  not a from-scratch survey of all ~194 jurisdictions. Extending
  coverage is additive: add one map to `catalog`, cite a real source,
  done -- never invent a jurisdiction's requirements to make coverage
  look bigger.

  This actor's own R0 scope is the SMR (Small Modular Reactor)
  generation-operator niche of ISIC 3511 only -- see README
  `Business-process coverage` -- so `:required-evidence` below is
  framed around SMR siting/licensing submission content specifically,
  not the full conventional/fossil-generation licensing regime ISIC
  3511 as a whole also covers.")

(def catalog
  "iso3 -> requirement map. `:required-evidence` names the general
  categories of supporting record an SMR siting/licensing submission
  draft would reference (site-safety-assessment-record/environmental-
  impact-record/security-plan-record/emergency-preparedness-plan-
  record); `:legal-basis` / `:owner-authority` / `:provenance` are the
  G2 citation the governor requires before any `:draft-licensing-
  submission` proposal can commit."
  {"JPN" {:name "Japan"
          :owner-authority "原子力規制委員会 (NRA, Nuclear Regulation Authority)"
          :legal-basis "核原料物質、核燃料物質及び原子炉の規制に関する法律 (Act on the Regulation of Nuclear Source Material, Nuclear Fuel Material and Reactors; \"Reactor Regulation Act\")"
          :national-spec "実用発電用原子炉の設置、運転等に関する規則 -- 設置許可申請の技術基準"
          :provenance "https://www.nra.go.jp/procedure/regulation/tekigousei/index.html"
          :required-evidence ["site-safety-assessment-record (敷地安全性評価記録)"
                              "environmental-impact-record (環境影響評価記録)"
                              "security-plan-record (核物質防護計画記録)"
                              "emergency-preparedness-plan-record (原子力災害対策計画記録)"]}
   "USA" {:name "United States"
          :owner-authority "U.S. Nuclear Regulatory Commission (NRC)"
          :legal-basis "10 CFR Part 50 (Domestic Licensing of Production and Utilization Facilities) / 10 CFR Part 52 (Licenses, Certifications, and Approvals for Nuclear Power Plants)"
          :national-spec "NRC Advanced Reactor / Small Modular Reactor licensing framework"
          :provenance "https://www.nrc.gov/reactors/new-reactors/smr.html"
          :required-evidence ["Site-safety-assessment record"
                              "Environmental-impact record"
                              "Security-plan record"
                              "Emergency-preparedness-plan record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "Office for Nuclear Regulation (ONR)"
          :legal-basis "Nuclear Installations Act 1965 (as amended) / Generic Design Assessment (GDA) process"
          :national-spec "ONR Small Modular Reactor licensing and permissioning guidance"
          :provenance "https://www.onr.org.uk/civil-nuclear-reactors/small-modular-reactors/"
          :required-evidence ["Site-safety-assessment record"
                              "Environmental-impact record"
                              "Security-plan record"
                              "Emergency-preparedness-plan record"]}})

(defn spec-basis
  "The jurisdiction's requirement map, or nil -- nil means NO
  spec-basis, and the governor must hold any `:draft-licensing-
  submission` proposal that tries to cite one anyway."
  [iso3]
  (get catalog iso3))

(defn coverage
  "Honest coverage report: how many of the requested jurisdictions
  actually have a spec-basis entry. Never report a missing
  jurisdiction as covered."
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-3511 R0: " (count catalog)
                 " jurisdictions seeded with an official SMR-licensing "
                 "spec-basis. This is a starting catalog, not a survey "
                 "of all ~194 jurisdictions -- extend `smrops.facts/"
                 "catalog`, never fabricate a jurisdiction's "
                 "requirements.")})))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
