(ns hive-ansatz.snapshot
  "Pure decisions of the proof-persistence pipeline: which overlay decls a
   snapshot selects, and what an import will add vs skip. One function =
   one decision; all effects live in hive-ansatz.persistence."
  (:require [malli.core :as m]
            [hive-ansatz.schema :as schema]))

(defn select
  "Filter `metas` by `spec` (drop :exclude names) and sort by :name."
  [metas spec]
  (let [exclude (or (:exclude spec) #{})]
    (->> metas
         (remove (fn [{:keys [name]}] (contains? exclude name)))
         (sort-by :name)
         vec)))

(m/=> select [:=> [:cat [:vector schema/DeclMeta] schema/SnapshotSpec]
              [:vector schema/DeclMeta]])

(defn theorem-names
  "Names of the metas that carry kernel-checkable proof terms."
  [metas]
  (->> metas (filter :theorem?) (mapv :name)))

(m/=> theorem-names [:=> [:cat [:vector schema/DeclMeta]] [:vector schema/DeclName]])

(defn import-plan
  "Partition `metas` into decls to :add (absent) vs :skip (already present),
   given `present?` — the set (or predicate) of names already in the env."
  [metas present?]
  {:add  (->> metas (remove (comp boolean present? :name)) (mapv :name))
   :skip (->> metas (filter (comp boolean present? :name)) (mapv :name))})

(m/=> import-plan [:=> [:cat [:vector schema/DeclMeta] :any] schema/ImportPlan])

(defn export-report
  [path metas]
  {:path path
   :count (count metas)
   :names (mapv :name metas)})

(m/=> export-report [:=> [:cat :string [:vector schema/DeclMeta]] schema/ExportReport])

(defn import-report
  [plan results]
  {:added (:add plan)
   :skipped (:skip plan)
   :all-verified (every? val results)
   :results results})

(m/=> import-report [:=> [:cat schema/ImportPlan schema/VerifyResults]
                     schema/ImportReport])
