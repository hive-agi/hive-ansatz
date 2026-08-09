(ns hive-ansatz.compile.oracle
  "Pure differential oracle over runtime/prover arms: build CheckResults, fold
   them into an OracleVerdict, and classify the verdict via an ordered OCP rule
   chain. No I/O — arms are gathered at the boundary and merely diffed here."
  (:require [malli.core :as m]
            [hive-ansatz.schema :as schema]))

(defn ->check
  "Build a CheckResult for `arm`/`probe`. When `ok?`, carry `v` as :value
   (stringified); otherwise carry it as :error."
  [arm probe ok? v]
  (cond-> {:arm arm :probe probe :ok? (boolean ok?)}
    ok?       (assoc :value (str v))
    (not ok?) (assoc :error (str v))))

(m/=> ->check [:=> [:cat schema/ArmKey :string :any :any] schema/CheckResult])

(defn verdict
  "Fold `results` (all for the same `probe`) into an OracleVerdict. :agree?
   iff results is non-empty, every arm ok?, and all values equal."
  [probe results]
  (let [rs (vec results)
        oks (filter :ok? rs)]
    {:probe probe
     :agree? (boolean (and (seq rs)
                           (every? :ok? rs)
                           (apply = (map :value oks))))
     :results rs}))

(m/=> verdict [:=> [:cat :string [:vector schema/CheckResult]] schema/OracleVerdict])

(defn proof->check
  "A proof arm's verify? boolean rendered as a CheckResult."
  [arm probe verify?]
  (->check arm probe true (boolean verify?)))

(m/=> proof->check [:=> [:cat schema/ArmKey :string :any] schema/CheckResult])

(defn proof-verdict
  "Differential PROOF-oracle: diff verify? across provers for one `probe`.
   `arm->verify` is an ordered seq of [arm verify?] pairs."
  [probe arm->verify]
  (verdict probe (mapv (fn [[arm v?]] (proof->check arm probe v?)) arm->verify)))

(m/=> proof-verdict
      [:=> [:cat :string [:sequential [:tuple schema/ArmKey :any]]] schema/OracleVerdict])

(defprotocol IVerdictRule
  "OCP rule: classify an OracleVerdict (+ optional LeanToolchainPin) into a
   category. Rules are tried in order; the first whose `applies?` is true wins."
  (applies? [this ctx])
  (category [this ctx]))

(defn- some-errored? [verdict]
  (boolean (some (comp not :ok?) (:results verdict))))

(def default-rules
  "Ordered verdict-classification rules (OCP: prepend/append to extend)."
  [(reify IVerdictRule
     (applies? [_ {:keys [verdict pin]}]
       (and pin (not (:aligned? pin)) (not (:agree? verdict))))
     (category [_ _] :version-skew))
   (reify IVerdictRule
     (applies? [_ {:keys [verdict]}]
       (and (some-errored? verdict) (not (:agree? verdict))))
     (category [_ _] :incomplete))
   (reify IVerdictRule
     (applies? [_ {:keys [verdict]}] (not (:agree? verdict)))
     (category [_ _] :disagree))
   (reify IVerdictRule
     (applies? [_ {:keys [verdict]}] (:agree? verdict))
     (category [_ _] :certified))])

(defn classify
  "Classify `verdict` into a category keyword via `rules` (default
   default-rules), given optional Lean toolchain `pin`. :unknown when no rule
   applies."
  ([verdict] (classify verdict nil default-rules))
  ([verdict pin] (classify verdict pin default-rules))
  ([verdict pin rules]
   (let [ctx {:verdict verdict :pin pin}]
     (or (some (fn [r] (when (applies? r ctx) (category r ctx))) rules)
         :unknown))))
