(ns hive-ansatz.assay.certify-test
  "Assay certifier contracts. The tier ladder is exercised through stub
   IProvers (no kernel); the proof rung is exercised against the live kernel
   only when ansatz is on the classpath."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [malli.core :as m]
            [hive-ansatz.assay.certify :as certify]
            [hive-ansatz.assay.claims :as claims]
            [hive-ansatz.ports :as ports]
            [hive-ansatz.schema :as schema]))

;; ---------------------------------------------------------------------------
;; Fixtures and stub ports
;; ---------------------------------------------------------------------------

(def factors {:tools [:carto :grep] :schema [:full :compact] :model ["a" "b" "c"]})

(defn- arms-for [reps]
  (vec (for [t [:carto :grep] sc [:full :compact] mo ["a" "b" "c"] rep (range reps)]
         {:arm/id (str t sc mo rep) :arm/levels {:tools t :schema sc :model mo}})))

(def design {:experiment/factors factors :experiment/arms (arms-for 2)})

(defrecord StubProver [calls define-result prove-fn]
  ports/IProver
  (define! [_ forms] (swap! calls conj [:define (count forms)]) define-result)
  (prove! [_ theorem _ prop _] (swap! calls conj [:prove theorem]) (prove-fn prop)))

(defn- stub
  ([] (stub nil (constantly nil)))
  ([define-result prove-fn] (->StubProver (atom []) define-result prove-fn)))

(defn- cert [result]
  (is (contains? result :ok) (pr-str result))
  (let [c (:ok result)]
    (is (m/validate schema/Certificate c) (pr-str (m/explain schema/Certificate c)))
    c))

;; ---------------------------------------------------------------------------
;; (a) balance
;; ---------------------------------------------------------------------------

(deftest balance-decision
  (testing "replicated full factorial is balanced"
    (is (:balanced? (claims/balance factors (arms-for 1))))
    (is (:balanced? (claims/balance factors (arms-for 3)))))
  (testing "a missing or duplicated cell is not"
    (is (not (:balanced? (claims/balance factors (pop (arms-for 2))))))
    (is (not (:balanced? (claims/balance factors (conj (arms-for 1) (first (arms-for 1)))))))))

(defspec balance-iff-equal-cell-counts 50
  (prop/for-all [counts (gen/vector (gen/choose 0 3) 12)]
    (let [cells (arms-for 1)
          arms (vec (mapcat (fn [a n] (repeat n a)) cells counts))]
      (= (:balanced? (claims/balance factors arms))
         (and (apply = counts) (pos? (first counts)))))))

(deftest balance-ill-posed
  (is (= :assay/undeclared-level
         (:error (claims/balance-obligations
                  (update design :experiment/arms conj
                          {:arm/id "x" :arm/levels {:tools :vim :schema :full :model "a"}})))))
  (is (= :assay/no-factors
         (:error (claims/balance-obligations {:experiment/factors {} :experiment/arms []})))))

;; ---------------------------------------------------------------------------
;; Tier ladder (stub prover)
;; ---------------------------------------------------------------------------

(deftest tier-proof-when-kernel-accepts
  (let [p (stub)
        c (cert (certify/certify-balance p design))]
    (is (= :proof (:certificate/tier c)))
    (is (:certificate/holds? c))
    (is (= [[:define (count hive-ansatz.assay.kernel/checker-forms)]]
           (take 1 @(:calls p))))
    (is (= 1 (count (filter (comp #{:prove} first) @(:calls p)))))))

(deftest tier-degrades-honestly
  (testing "no prover -> :computed, reason :no-kernel"
    (let [c (cert (certify/certify-balance nil design))]
      (is (= :computed (:certificate/tier c)))
      (is (= :no-kernel (get-in c [:certificate/degraded :reason])))))
  (testing "checkers fail to elaborate -> :computed, nothing proven"
    (let [p (stub {:error "boom"} (constantly nil))
          c (cert (certify/certify-balance p design))]
      (is (= :computed (:certificate/tier c)))
      (is (= :define-failed (get-in c [:certificate/degraded :reason])))
      (is (not-any? (comp #{:prove} first) @(:calls p)))))
  (testing "kernel rejects -> :computed, never :proof"
    (let [c (cert (certify/certify-balance (stub nil (constantly "rejected")) design))]
      (is (= :computed (:certificate/tier c)))
      (is (= :kernel-rejected (get-in c [:certificate/degraded :reason])))
      (is (every? #(= :rejected (:status %)) (:certificate/evidence c))))))

;; ---------------------------------------------------------------------------
;; (c) promoted-arm invariants
;; ---------------------------------------------------------------------------

(def invariants
  [{:invariant/kind :levels-declared}
   {:invariant/kind :implies :invariant/if [:schema :compact] :invariant/then [:tools :carto]}
   {:invariant/kind :excludes :invariant/if [:tools :grep] :invariant/then [:model "c"]}
   {:invariant/kind :within-budget :invariant/k 1}])

(def incumbent {:arm/id "inc" :arm/levels {:tools :carto :schema :full :model "a"}})

(deftest arm-invariants
  (is (every? #(m/validate schema/Invariant %) invariants))
  (testing "satisfying arm"
    (let [c (cert (certify/certify-arm (stub) factors
                                       {:arm/id "p" :arm/levels {:tools :carto :schema :compact :model "a"}}
                                       incumbent invariants))]
      (is (:certificate/holds? c))
      (is (= :proof (:certificate/tier c)))))
  (testing "each violated invariant is reported false"
    (let [ok? (fn [levels]
                (->> (certify/certify-arm nil factors {:arm/id "p" :arm/levels levels} incumbent invariants)
                     :ok :certificate/evidence (mapv :expected)))]
      ;; differs from the incumbent in two factors, so the k=1 budget fails too
      (is (= [true false true false] (ok? {:tools :grep :schema :compact :model "a"})))
      (is (= [true true false false] (ok? {:tools :grep :schema :full :model "c"})))
      (is (= [true true true false] (ok? {:tools :carto :schema :compact :model "b"})))
      (is (= [false true true true] (ok? {:tools :carto :schema :full :model "z"})))))
  (testing "budget needs an incumbent; unknown levels are ill-posed"
    (is (= :assay/no-incumbent
           (:error (certify/certify-arm nil factors incumbent nil invariants))))
    (is (= :assay/unknown-level
           (:error (certify/certify-arm nil factors incumbent incumbent
                                        [{:invariant/kind :implies :invariant/if [:tools :vim]
                                          :invariant/then [:model "a"]}]))))))

;; ---------------------------------------------------------------------------
;; (b) prompt independence
;; ---------------------------------------------------------------------------

(deftest prompt-independence
  (let [arms (arms-for 1)]
    (testing "levels-only render is independent, at :property tier"
      (let [c (cert (certify/certify-prompt-independence #(str "solve with " (:arm/levels %)) arms))]
        (is (:certificate/holds? c))
        (is (= :property (:certificate/tier c)))))
    (testing "a render that leaks the arm id is caught"
      (let [c (cert (certify/certify-prompt-independence #(str "arm " (:arm/id %)) arms))]
        (is (false? (:certificate/holds? c)))
        (is (seq (:leaking-ids (first (:certificate/evidence c)))))))
    (testing "a throwing render is an error Result, not a certificate"
      (is (= :assay/render-failed
             (:error (certify/certify-prompt-independence (fn [_] (throw (ex-info "x" {}))) arms)))))))

;; ---------------------------------------------------------------------------
;; Proof rung against the live kernel (only when ansatz is present)
;; ---------------------------------------------------------------------------

(deftest kernel-proof-rung
  (if-let [kp (certify/default-prover)]
    (let [good (cert (certify/certify-balance kp design))
          bad (cert (certify/certify-balance kp (update design :experiment/arms pop)))]
      (is (= [:proof true] ((juxt :certificate/tier :certificate/holds?) good)))
      (testing "the kernel proves the decider is FALSE for an unbalanced design"
        (is (= [:proof false] ((juxt :certificate/tier :certificate/holds?) bad)))))
    (is (nil? (certify/default-prover)) "ansatz absent: proof rung not exercised")))
