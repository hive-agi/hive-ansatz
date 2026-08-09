(ns hive-ansatz.schema-test
  "Generation + validation smoke for the native-kernels value objects."
  (:require [clojure.test :refer [deftest is testing]]
            [malli.core :as m]
            [malli.generator :as mg]
            [hive-ansatz.schema :as schema]))

(deftest value-objects-generate
  (doseq [s [schema/CompileTarget schema/CheckResult schema/OracleVerdict
             schema/KernelManifest schema/LeanToolchainPin
             schema/LeanBootProbe schema/LeanBootDecision]]
    (testing (pr-str s)
      (is (m/validate s (mg/generate s {:size 6}))))))
