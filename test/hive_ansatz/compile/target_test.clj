(ns hive-ansatz.compile.target-test
  "Provider-as-data (DIP) + OCP contracts for the ICompileTarget registry,
   plus a stub-injected port-conformance check (tests obey DIP)."
  (:require [clojure.test :refer [deftest testing is]]
            [malli.core :as m]
            [hive-ansatz.schema :as schema]
            [hive-ansatz.compile.target :as tgt]))

(deftest profiles-conform
  (doseq [[k p] tgt/target-profiles]
    (testing (str k)
      (is (m/validate schema/CompileTarget p)
          (pr-str (m/explain schema/CompileTarget p)))
      (is (= k (:target p)) "registry key matches :target"))))

(deftest exactly-one-oracle
  (is (= 1 (count (filter :oracle? (vals tgt/target-profiles)))))
  (is (= :jvm (tgt/oracle-target))))

(deftest cljrs-requires-zero-interpreted-fallback
  (is (contains? (:gates (tgt/profile :cljrs)) :zero-interpreted-fallback)))

(deftest ocp-fourth-target-is-data
  (let [jank {:target :jank :dialect-key :clj :opacity :native-code
              :oracle? false :artifact :native-lib :gates #{}}
        extended (tgt/add-profile tgt/target-profiles jank)]
    (is (= 4 (count extended)))
    (is (every? #(m/validate schema/CompileTarget %) (vals extended)))
    (is (= jank (get extended :jank)))))

(defrecord StubTarget [k]
  tgt/ICompileTarget
  (target-key [_] k)
  (compile-kernel [_ kernel] {:artifact k :kernel kernel})
  (run-probe [_ artifact probe]
    {:arm k :probe probe :ok? true :value (pr-str artifact)}))

(deftest stub-conforms-to-port
  (let [t (->StubTarget :stub)
        art (tgt/compile-kernel t {:name "id"})
        res (tgt/run-probe t art "p1")]
    (is (= :stub (tgt/target-key t)))
    (is (m/validate schema/CheckResult res)
        (pr-str (m/explain schema/CheckResult res)))))
