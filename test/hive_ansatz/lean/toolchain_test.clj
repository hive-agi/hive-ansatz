(ns hive-ansatz.lean.toolchain-test
  "Trifecta for the pure warm-boot gate + an explicit classification table."
  (:require [clojure.test :refer [deftest is]]
            [hive-schemas.test :as hst]
            [hive-ansatz.schema :as schema]
            [hive-ansatz.lean.toolchain :as tc]))

(hst/deftrifecta-from-schema boot-decision-conforms
  hive-ansatz.lean.toolchain/boot-decision
  {:in schema/LeanBootProbe
   :out schema/LeanBootDecision
   :rel (fn [{:keys [enabled? toolchain-present? free-disk-gb free-ram-gb]} out]
          (= out
             (cond
               (not enabled?)                  {:boot? false :reason :disabled}
               (not toolchain-present?)        {:boot? false :reason :no-toolchain}
               (< free-disk-gb tc/min-disk-gb) {:boot? false :reason :insufficient-disk}
               (< free-ram-gb tc/min-ram-gb)   {:boot? false :reason :insufficient-ram}
               :else                           {:boot? true  :reason :ok})))
   :mutation true
   :num-tests 100})

(deftest gate-table
  (is (= {:boot? false :reason :disabled}
         (tc/boot-decision {:enabled? false :toolchain-present? true :free-disk-gb 999 :free-ram-gb 999})))
  (is (= {:boot? false :reason :no-toolchain}
         (tc/boot-decision {:enabled? true :toolchain-present? false :free-disk-gb 999 :free-ram-gb 999})))
  (is (= {:boot? false :reason :insufficient-disk}
         (tc/boot-decision {:enabled? true :toolchain-present? true :free-disk-gb 0 :free-ram-gb 999})))
  (is (= {:boot? false :reason :insufficient-ram}
         (tc/boot-decision {:enabled? true :toolchain-present? true :free-disk-gb 999 :free-ram-gb 0})))
  (is (= {:boot? true :reason :ok}
         (tc/boot-decision {:enabled? true :toolchain-present? true :free-disk-gb 999 :free-ram-gb 999}))))
