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

;; ── ICompileTarget: provider-behaviour-as-DATA ──────────────────────────────

(def CompileTargetKey
  "Registry key of a compile-target profile (e.g. :jvm :cljw :cljrs)."
  :keyword)

(def Opacity
  "How much kernel source survives into the shipped artifact."
  [:enum :none :bytecode :native-code])

(def CompileTarget
  "One ICompileTarget provider as DATA; the registry of these is the DIP swap
   point, and a new target is a data entry (OCP), not a new protocol impl."
  [:map {:closed true}
   [:target CompileTargetKey]
   [:dialect-key :keyword]
   [:opacity Opacity]
   [:oracle? :boolean]
   [:artifact :keyword]
   [:gates [:set :keyword]]])

;; ── Differential oracle (proof-level P8 and value-level P5/P6) ───────────────

(def ArmKey
  "Identifier of one oracle arm — a runtime or a prover (:jvm :cljrs :ansatz
   :lean-repl …)."
  :keyword)

(def CheckResult
  "One arm's outcome for one probe: the pr-str'd value it produced, or an
   error class if it failed."
  [:map {:closed true}
   [:arm ArmKey]
   [:probe :string]
   [:ok? :boolean]
   [:value {:optional true} :string]
   [:error {:optional true} :string]])

(def OracleVerdict
  "Differential outcome across arms for one probe. :agree? iff every arm
   succeeded and produced the same value."
  [:map {:closed true}
   [:probe :string]
   [:agree? :boolean]
   [:results [:vector CheckResult]]])

;; ── Kernel + Lean toolchain provenance ──────────────────────────────────────

(def ProofProvenance
  "Where a kernel's proof term originated."
  [:enum :hand-written :kimina-prover :lean-ingest])

(def KernelManifest
  "Descriptor of one extractable proof-carrying kernel: identity, proof
   provenance, admitted compile targets, and opacity class."
  [:map {:closed true}
   [:name DeclName]
   [:source-ns :string]
   [:provenance ProofProvenance]
   [:targets [:vector CompileTargetKey]]
   [:opacity Opacity]
   [:spec {:optional true} :string]])

(def LeanToolchainPin
  "Versions a Lean IProofEnv speaks, carried on differential results so the
   ansatz-export-rev ↔ lean-rev alignment caveat stays explicit. :aligned? is
   a claim, not a proof."
  [:map {:closed true}
   [:lean-version :string]
   [:mathlib-rev :string]
   [:ansatz-export-rev :string]
   [:aligned? :boolean]])

;; ── Gated Lean warm-boot decision (mirrors the mathlib :7899 gate) ───────────

(def LeanBootProbe
  "Inputs to the gated Lean warm-boot decision."
  [:map {:closed true}
   [:enabled? :boolean]
   [:toolchain-present? :boolean]
   [:free-disk-gb :int]
   [:free-ram-gb :int]])

(def LeanBootDecision
  "The gate's ruling plus the reason keyword."
  [:map {:closed true}
   [:boot? :boolean]
   [:reason :keyword]])
