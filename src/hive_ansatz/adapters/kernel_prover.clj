(ns hive-ansatz.adapters.kernel-prover
  "IProver over the live ansatz kernel (needs ansatz via local.deps.edn).
   define! skips forms whose constant is already present, so repeated
   certification against one env stays idempotent; prove! is
   hive-schemas.proven/proof-failure (nil | rejection message)."
  (:require [ansatz.core :as a]
            [ansatz.kernel.env :as env]
            [ansatz.kernel.name :as nm]
            [hive-ansatz.ports :as ports]
            [hive-schemas.proven :as proven]))

(defonce ^:private booted
  (delay (binding [a/*verbose* false]
           (when (or (nil? @a/ansatz-env)
                     (nil? (env/lookup (a/env) (nm/from-string "Nat"))))
             (a/load-init!)))))

(def ^:private forms-ns
  (let [n (create-ns 'hive-ansatz.kernel-forms)]
    (binding [*ns* n] (refer 'clojure.core))
    n))

(defn- present? [form]
  (some? (env/lookup (a/env) (nm/from-string (str (second form))))))

(defrecord KernelProver []
  ports/IProver
  (define! [_ forms]
    @booted
    (try
      (binding [a/*verbose* false *ns* forms-ns]
        (doseq [f forms :when (not (present? f))] (eval f)))
      nil
      (catch Throwable e {:error (.getMessage (or (.getCause e) e))})))
  (prove! [_ theorem params prop tactics]
    @booted
    (proven/proof-failure (symbol theorem) params prop tactics)))

(defn kernel-prover [] (->KernelProver))
