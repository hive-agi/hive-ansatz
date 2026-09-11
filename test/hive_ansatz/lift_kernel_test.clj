(ns hive-ansatz.lift-kernel-test
  "The lift pipeline over the REAL ansatz kernel: the forms a hive-shape tier
   lifts for `add` are defined, the right identity is proven for all
   naturals, the artifact is persisted through hive-fressian and re-verifies
   on import. Measured only when the kernel is on the classpath
   (local.deps.edn); says so when it is not."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [malli.core :as m]
            [hive-ansatz.certify :as certify]
            [hive-ansatz.persistence :as persistence]
            [hive-ansatz.schema :as schema])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

(defn- adapter [sym]
  (try (requiring-resolve sym) (catch Throwable _ nil)))

(def ^:private the-lift
  {:name  'add
   :forms '[(malli.core/=> add [:=> [:cat :int :int] :int])
            (ansatz.core/defn add [a b] (+ a b))]})

(defn- obligation [theorem prop]
  {:law-id   :foreign/reference.add
   :property {:theorem theorem
              :params  '[x :- Nat]
              :prop    prop
              :tactics '[(induction x) (all_goals (simp_all [add])) (all_goals (try (omega)))]}})

(defn- temp-store []
  (str (Files/createTempDirectory "lift-store" (make-array FileAttribute 0))))

(defn- stored-count [store]
  (count (.listFiles (io/file store))))

(deftest ^:kernel add-lifted-by-a-tier-is-proven-and-persisted
  (let [live-env       (adapter 'hive-ansatz.adapters.ansatz/live-env)
        fressian-codec (adapter 'hive-ansatz.adapters.ansatz/fressian-codec)]
    (if-not (and live-env fressian-codec)
      (println "SKIP: the ansatz kernel is not on the classpath; the lift was not measured")
      (let [env    (live-env)
            codec  (fressian-codec)
            store  (temp-store)
            ports  {:prover env :env env :info env :codec codec :store store}
            report (certify/prove-lift! ports the-lift
                                        (obligation 'hs-lift-add-right-id '(= Nat ((add x) 0) x)))]
        (is (:proven? report) (pr-str report))
        (is (m/validate schema/ArtifactRef (:artifact-ref report)))
        (is (contains? (set (:names report)) "add"))
        (is (contains? (set (:names report)) "hs-lift-add-right-id"))
        (testing "the stored artifact re-verifies through the kernel"
          (let [{:keys [all-verified results]} (persistence/import! env env codec (:path report))]
            (is all-verified (pr-str results))
            (is (true? (get results "hs-lift-add-right-id")))))
        (testing "a false property is refused and stores no artifact"
          (let [before  (stored-count store)
                refused (certify/prove-lift! ports the-lift
                                             (obligation 'hs-lift-add-false
                                                         '(= Nat ((add x) 0) (Nat.succ x))))]
            (is (false? (:proven? refused)))
            (is (= [:kernel-rejected] (:reasons refused)))
            (is (= before (stored-count store)))))))))
