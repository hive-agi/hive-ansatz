(ns hive-ansatz.compile.oracle-test
  "Trifecta for the pure verdict fold + hand-written diff laws, OCP rule-chain
   order, and a stub-IProofEnv differential (DIP)."
  (:require [clojure.test :refer [deftest testing is]]
            [hive-schemas.test :as hst]
            [hive-ansatz.schema :as schema]
            [hive-ansatz.ports :as ports]
            [hive-ansatz.compile.oracle :as oracle]))

(hst/deftrifecta-from-schema verdict-conforms
  hive-ansatz.compile.oracle/verdict
  {:in [:cat :string [:vector schema/CheckResult]]
   :out schema/OracleVerdict
   :rel (fn [[probe results] out]
          (let [oks (filter :ok? results)]
            (and (= (:probe out) probe)
                 (= (:results out) (vec results))
                 (= (:agree? out)
                    (boolean (and (seq results)
                                  (every? :ok? results)
                                  (apply = (map :value oks))))))))
   :mutation true
   :num-tests 100})

(deftest proof-verdict-diff-laws
  (testing "provers agreeing on verify? -> agree?"
    (is (:agree? (oracle/proof-verdict "t" [[:ansatz true] [:lean-repl true]]))))
  (testing "provers disagreeing -> not agree?"
    (is (not (:agree? (oracle/proof-verdict "t" [[:ansatz true] [:lean-repl false]]))))))

(deftest classify-rule-order
  (let [certified  (oracle/verdict "p" [{:arm :jvm :probe "p" :ok? true :value "1"}
                                        {:arm :cljrs :probe "p" :ok? true :value "1"}])
        disagree   (oracle/verdict "p" [{:arm :jvm :probe "p" :ok? true :value "1"}
                                        {:arm :cljrs :probe "p" :ok? true :value "2"}])
        incomplete (oracle/verdict "p" [{:arm :jvm :probe "p" :ok? true :value "1"}
                                        {:arm :cljrs :probe "p" :ok? false :error "AOT-nil"}])
        aligned    {:lean-version "4" :mathlib-rev "a" :ansatz-export-rev "a" :aligned? true}
        skewed     {:lean-version "4" :mathlib-rev "a" :ansatz-export-rev "b" :aligned? false}]
    (is (= :certified (oracle/classify certified)))
    (is (= :disagree (oracle/classify disagree)))
    (is (= :incomplete (oracle/classify incomplete)))
    (testing "version-skew rule precedes disagree when pin misaligned"
      (is (= :version-skew (oracle/classify disagree skewed))))
    (testing "aligned pin does not mask a real disagreement"
      (is (= :disagree (oracle/classify disagree aligned))))))

(defrecord StubEnv [verify-ret]
  ports/IProofEnv
  (overlay [_] [])
  (present? [_ _] true)
  (add-decl! [_ _] nil)
  (verify [_ _decl] verify-ret))

(deftest differential-over-stub-envs
  (let [a (->StubEnv true) b (->StubEnv true) c (->StubEnv false)
        decl :some-decl
        v-agree (oracle/proof-verdict "thm"
                  [[:ansatz (ports/verify a decl)] [:lean-repl (ports/verify b decl)]])
        v-diff  (oracle/proof-verdict "thm"
                  [[:ansatz (ports/verify a decl)] [:lean-repl (ports/verify c decl)]])]
    (is (:agree? v-agree))
    (is (not (:agree? v-diff)))))
