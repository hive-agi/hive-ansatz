(ns hive-ansatz.assay.claims
  "PURE structural claims about assay designs and arms, as obligations.

   Assay values arrive as PLAIN DATA (hive-assay keys, no hive-assay dep):
     design  {:experiment/factors {fk [level ...]}
              :experiment/arms    [{:arm/id s :arm/levels {fk level}} ...]}
     arm     {:arm/id s :arm/levels {fk level}}

   Levels are encoded as indices into their factor's declared level vector,
   factors ordered by (str fk); a design cell is the mixed-radix code of an
   arm's indices. Each obligation carries the host-computed truth value
   (:expected) and the kernel decider expression that must reduce to it —
   the boundary decides which tier discharges it."
  (:require [clojure.string :as str]
            [malli.core :as m]
            [hive-dsl.result :as r]
            [hive-ansatz.assay.kernel :as k]
            [hive-ansatz.schema :as schema]))

;; ---------------------------------------------------------------------------
;; Encoding
;; ---------------------------------------------------------------------------

(defn factor-order
  "Factor keys in canonical order."
  [factors]
  (vec (sort-by str (keys factors))))

(defn radices [factors]
  (mapv #(count (get factors %)) (factor-order factors)))

(defn level-index
  "Index of `level` among fk's declared levels; the radix (one past the last
   index) when undeclared, so range checks fail rather than collide."
  [factors fk level]
  (let [levels (vec (get factors fk))
        i (.indexOf ^java.util.List levels level)]
    (if (neg? i) (count levels) i)))

(defn arm-indices [factors arm]
  (mapv #(level-index factors % (get (:arm/levels arm) %)) (factor-order factors)))

(defn declared-arm?
  "Every declared factor is set to a declared level and no other key is set."
  [factors arm]
  (let [levels (:arm/levels arm)]
    (and (= (set (keys levels)) (set (keys factors)))
         (every? (fn [[fk v]] (some #(= v %) (get factors fk))) levels))))

(defn cell-code [radices idxs]
  (reduce (fn [acc [radix i]] (+ (* acc radix) i)) 0 (map vector radices idxs)))

(defn- theorem-name [claim expr expected]
  (let [h (Integer/toHexString (hash [expr expected]))]
    (str k/prefix (str/replace (name claim) "-" "_") "_" h)))

(defn- obligation [claim id expr expected]
  {:obligation/id id
   :obligation/theorem (theorem-name claim expr expected)
   :obligation/prop (k/prop expr expected)
   :obligation/expected expected})

;; ---------------------------------------------------------------------------
;; (a) Full-factorial balance
;; ---------------------------------------------------------------------------

(defn balance
  "Host decision for design balance: every cell of the full product occurs
   the same number r >= 1 of times. Balanced implies every level pair (hence
   every main effect and interaction) is observed equally often."
  [factors arms]
  (let [rs (radices factors)
        n (reduce * 1 rs)
        codes (mapv #(cell-code rs (arm-indices factors %)) arms)
        freqs (frequencies codes)
        rep (quot (count codes) (max n 1))]
    {:cells n
     :replicates rep
     :codes codes
     :balanced? (and (pos? n) (pos? rep)
                     (= (count codes) (* rep n))
                     (= (count freqs) n)
                     (every? #(= rep %) (vals freqs)))}))

(defn balance-obligations
  "Obligations for claim :assay/balanced-design, or an error Result when an
   arm is not a cell of the declared factor space (the claim is then
   ill-posed, not false)."
  [{:experiment/keys [factors arms]}]
  (cond
    (empty? factors) (r/err :assay/no-factors {})
    (some (comp empty? val) factors) (r/err :assay/empty-factor {:factors (keys (filter (comp empty? val) factors))})
    :else
    (if-let [bad (seq (remove #(declared-arm? factors %) arms))]
      (r/err :assay/undeclared-level {:arms (mapv :arm/id bad)})
      (let [{:keys [cells replicates codes balanced?]} (balance factors arms)
            rep (max replicates 1)]
        (r/ok {:claim :assay/balanced-design
               :facts {:cells cells :replicates replicates :arms (count arms)}
               :obligations [(obligation :balanced-design :balanced
                                         (k/balanced-expr cells rep codes)
                                         balanced?)]})))))

;; ---------------------------------------------------------------------------
;; (c) Promoted-arm invariants
;; ---------------------------------------------------------------------------

(defn- position [factors fk] (.indexOf ^java.util.List (factor-order factors) fk))

(defn- known-level? [factors [fk v]]
  (and (contains? factors fk) (some #(= v %) (get factors fk))))

(defn- invariant-obligation
  [factors idxs incumbent-idxs i {:invariant/keys [kind] :as inv}]
  (let [id (keyword "invariant" (str i "-" (name kind)))]
    (case kind
      :levels-declared
      (let [rs (radices factors)]
        (r/ok (obligation :arm-invariants id (k/in-range-expr rs idxs)
                          (every? true? (map < idxs rs)))))

      (:implies :excludes)
      (let [{c :invariant/if t :invariant/then} inv]
        (if-not (and (known-level? factors c) (known-level? factors t))
          (r/err :assay/unknown-level {:invariant inv})
          (let [[cf cv] c [tf tv] t
                pi (position factors cf) pj (position factors tf)
                a (level-index factors cf cv) b (level-index factors tf tv)
                ante (= a (nth idxs pi))
                cons' (= b (nth idxs pj))
                negate? (= kind :excludes)]
            (r/ok (obligation :arm-invariants id
                              (k/implies-expr idxs pi a pj b negate?)
                              (or (not ante) (if negate? (not cons') cons')))))))

      :within-budget
      (let [budget (:invariant/k inv)]
        (cond
          (nil? incumbent-idxs) (r/err :assay/no-incumbent {:invariant inv})
          (not (nat-int? budget)) (r/err :assay/bad-budget {:invariant inv})
          :else
          (r/ok (obligation :arm-invariants id (k/within-expr idxs incumbent-idxs budget)
                            (<= (count (filter false? (map = idxs incumbent-idxs))) budget)))))

      (r/err :assay/unknown-invariant {:invariant inv}))))

(defn invariant-obligations
  "Obligations for claim :assay/arm-invariants: `arm` against `invariants`
   (data, see schema/Invariant) over the declared `factors`; `incumbent` is
   the arm it would replace (needed by :within-budget)."
  [factors arm incumbent invariants]
  (if (empty? factors)
    (r/err :assay/no-factors {})
    (let [idxs (arm-indices factors arm)
            inc-idxs (some->> incumbent (arm-indices factors))
            results (map-indexed #(invariant-obligation factors idxs inc-idxs %1 %2) invariants)]
      (if-let [e (first (filter r/err? results))]
        e
        (r/ok {:claim :assay/arm-invariants
               :facts {:arm (:arm/id arm) :invariants (count invariants)}
               :obligations (mapv :ok results)})))))

(m/=> cell-code [:=> [:cat [:sequential nat-int?] [:sequential nat-int?]] nat-int?])
(m/=> balance-obligations [:=> [:cat schema/AssayDesign] :map])
