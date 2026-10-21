(ns hive-ansatz.schema
  "Malli value objects for the hive-ansatz bounded context.

   Boundary shapes are permissive (tolerate MCP string coercion); internal
   plan/report shapes are closed. The schema is the single source: it drives
   the m/=> contracts on the pure snapshot fns AND the synthesized tests.")

(def DeclName
  "A declaration name as exported by a prover kernel (non-blank string)."
  [:and :string [:fn {:error/message "non-blank"} (fn [s] (pos? (count s)))]])

(def DeclMeta
  "Host-neutral metadata for one kernel declaration.
   :theorem? — the decl carries a proof term the kernel can re-check."
  [:map {:closed true}
   [:name DeclName]
   [:theorem? :boolean]])

(def SnapshotSpec
  "Selection spec for an export: which overlay decls to snapshot."
  [:map
   [:exclude {:optional true} [:set :string]]])

(def ImportPlan
  "Pure decision: which decls an import will add vs skip."
  [:map {:closed true}
   [:add [:vector DeclName]]
   [:skip [:vector DeclName]]])

(def ExportReport
  [:map {:closed true}
   [:path :string]
   [:count :int]
   [:names [:vector DeclName]]])

(def VerifyResults
  "Per-theorem kernel verification outcome."
  [:map-of :string :boolean])

(def ImportReport
  [:map {:closed true}
   [:added [:vector DeclName]]
   [:skipped [:vector DeclName]]
   [:all-verified :boolean]
   [:results VerifyResults]])

(def Idiom
  "One proving strategy/idiom as data (see hive-ansatz.recipes)."
  [:map {:closed true}
   [:id :keyword]
   [:title :string]
   [:rule :string]
   [:fix {:optional true} :string]
   [:tags [:set :keyword]]])

;; ---------------------------------------------------------------------------
;; Assay certificates (hive-assay values consumed as plain data)
;; ---------------------------------------------------------------------------

(def AssayArm
  "An assay arm as plain data: the hive-assay :arm/* keys this context reads."
  [:map
   [:arm/id [:and :string [:fn {:error/message "non-blank"} (fn [s] (pos? (count s)))]]]
   [:arm/levels [:map-of :any :any]]])

(def AssayDesign
  "An assay experiment's structural part: declared factors and the arms."
  [:map
   [:experiment/factors [:map-of :any [:sequential :any]]]
   [:experiment/arms [:sequential AssayArm]]])

(def LevelRef
  "[factor-key level] naming one declared level."
  [:tuple :any :any])

(def Invariant
  "A declared structural invariant a promoted arm must satisfy.
   :levels-declared  every factor set to a declared level
   :implies          arm at level :if  => arm at level :then
   :excludes         arm at level :if  => arm NOT at level :then
   :within-budget    differs from the incumbent in at most :k factors"
  [:multi {:dispatch :invariant/kind}
   [:levels-declared [:map [:invariant/kind [:= :levels-declared]]]]
   [:implies [:map [:invariant/kind [:= :implies]]
              [:invariant/if LevelRef] [:invariant/then LevelRef]]]
   [:excludes [:map [:invariant/kind [:= :excludes]]
               [:invariant/if LevelRef] [:invariant/then LevelRef]]]
   [:within-budget [:map [:invariant/kind [:= :within-budget]]
                    [:invariant/k nat-int?]]]])

(def CertificateTier
  "Evidence rung, strongest first. :proof — the ansatz kernel checked a
   proof term of the claim's decider equation; :computed — the host decided
   the claim exhaustively over the finite data (no kernel); :property — the
   claim was sampled (holds on every sample, not on all inputs)."
  [:enum :proof :computed :property])

(def Certificate
  "A STRUCTURAL certificate. It never ranks arms: whether an arm is better
   is a statistical claim that belongs to hive-assay."
  [:map {:closed true}
   [:certificate/claim [:enum :assay/balanced-design :assay/prompt-arm-independent
                        :assay/arm-invariants]]
   [:certificate/holds? :boolean]
   [:certificate/tier CertificateTier]
   [:certificate/subject :map]
   [:certificate/evidence [:vector :map]]
   [:certificate/degraded {:optional true} [:map-of :keyword :any]]])
