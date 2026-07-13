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
