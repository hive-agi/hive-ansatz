(ns hive-ansatz.snapshot-test
  "Trifecta + contracts for the pure snapshot decisions and the
   persistence boundary (DIP: exercised through fake ports).

   The property + mutation facets are synthesized from the malli schemas
   by hive-schemas.test. Hand-written below only what a schema cannot
   state: exclusion/order contracts, plan partition laws, and the
   port-composition contract of export!/import!."
  (:require [clojure.test :refer [deftest testing is]]
            [malli.generator :as mg]
            [hive-schemas.test :as hst]
            [hive-ansatz.schema :as schema]
            [hive-ansatz.snapshot :as snapshot]
            [hive-ansatz.ports :as ports]
            [hive-ansatz.persistence :as persistence]))

;; ============================================================================
;; Schema-synthesized
;; ============================================================================

;; :mutation false — the output is a vector of scalars; the corruption engine
;; derives mutants from map ENTRIES and would yield none.
(hst/deftrifecta-from-schema theorem-names-conform
  hive-ansatz.snapshot/theorem-names
  {:in       [:vector schema/DeclMeta]
   :out      [:vector schema/DeclName]
   :rel      (fn [in out]
               (= out (->> in (filter :theorem?) (mapv :name))))
   :mutation false
   :num-tests 100})

;; ============================================================================
;; Hand-written — contracts a shape cannot state
;; ============================================================================

(deftest select-contract
  (doseq [metas (repeatedly 50 #(mg/generate [:vector schema/DeclMeta] {:size 8}))]
    (let [exclude (set (map :name (take 2 metas)))
          out (snapshot/select (vec metas) {:exclude exclude})]
      (testing "sorted by name"
        (is (= (map :name out) (sort (map :name out)))))
      (testing "excluded names absent"
        (is (empty? (filter (comp exclude :name) out))))
      (testing "subset of input"
        (is (every? (set metas) out))))))

(deftest select-no-spec-keeps-all
  (let [metas [{:name "b" :theorem? true} {:name "a" :theorem? false}]]
    (is (= ["a" "b"] (mapv :name (snapshot/select metas {}))))))

(deftest import-plan-partition-laws
  (doseq [metas (repeatedly 50 #(mg/generate [:vector schema/DeclMeta] {:size 8}))]
    (let [metas (vec (vals (into {} (map (juxt :name identity)) metas)))  ;; unique names
          present (set (map :name (take-nth 2 metas)))
          {:keys [add skip]} (snapshot/import-plan metas present)]
      (testing "disjoint"
        (is (empty? (filter (set add) skip))))
      (testing "complete"
        (is (= (set (map :name metas)) (into (set add) skip))))
      (testing "skip ⊆ present"
        (is (every? present skip))))))

;; ============================================================================
;; DIP contract — persistence boundary over fake ports
;; ============================================================================

(defrecord FakePorts [env-names store verify-result]
  ports/IProofEnv
  (overlay [_] (vec (vals @env-names)))
  (present? [_ n] (contains? @env-names n))
  (add-decl! [_ d] (swap! env-names assoc (:n d) d) nil)
  (verify [_ _] verify-result)

  ports/IDeclInfo
  (decl-name [_ d] (:n d))
  (theorem? [_ d] (boolean (:thm d)))

  ports/IDeclCodec
  (write-decls! [_ path decls] (swap! store assoc path (vec decls)) (count decls))
  (read-decls [_ path] {:header {:format :fake} :decls (get @store path [])}))

(defn- fake [decls verify-result]
  (->FakePorts (atom (into {} (map (juxt :n identity)) decls))
               (atom {})
               verify-result))

(deftest export-import-roundtrip-through-ports
  (let [decls [{:n "thm_a" :thm true} {:n "def_b" :thm false} {:n "thm_c" :thm true}]
        src (fake decls true)
        report (persistence/export! src src src "snap" {:exclude #{"def_b"}})]
    (testing "export selects, sorts, excludes"
      (is (= {:path "snap" :count 2 :names ["thm_a" "thm_c"]} report)))
    (testing "import into empty env adds all and verifies theorems"
      (let [dst (->FakePorts (atom {}) (:store src) true)
            r (persistence/import! dst dst dst "snap")]
        (is (= ["thm_a" "thm_c"] (:added r)))
        (is (= [] (:skipped r)))
        (is (:all-verified r))
        (is (= {"thm_a" true "thm_c" true} (:results r)))))
    (testing "re-import skips everything, still verifies"
      (let [dst (->FakePorts (atom {}) (:store src) true)]
        (persistence/import! dst dst dst "snap")
        (let [r (persistence/import! dst dst dst "snap")]
          (is (= [] (:added r)))
          (is (= ["thm_a" "thm_c"] (:skipped r))))))
    (testing "failed kernel check surfaces as :all-verified false"
      (let [dst (->FakePorts (atom {}) (:store src) false)
            r (persistence/import! dst dst dst "snap")]
        (is (not (:all-verified r)))))))

(deftest inspect-does-not-mutate
  (let [decls [{:n "thm_a" :thm true}]
        src (fake decls true)]
    (persistence/export! src src src "snap" {})
    (let [dst (->FakePorts (atom {}) (:store src) true)
          r (persistence/inspect dst dst "snap")]
      (is (= [{:name "thm_a" :theorem? true}] (:metas r)))
      (is (false? (ports/present? dst "thm_a"))))))
