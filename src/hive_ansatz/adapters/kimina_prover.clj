(ns hive-ansatz.adapters.kimina-prover
  "IProofSource over MoonshotAI/Kimina-Prover (spec -> Lean-4 proof). Boundary:
   model invocation is scaffolded (TODO)."
  (:require [hive-ansatz.ports :as ports]))

(defrecord KiminaProver [client]
  ports/IProofSource
  (propose [_ _spec]
    (throw (ex-info "TODO: Kimina-Prover spec->proof" {:adapter :kimina-prover}))))

(defn kimina-prover
  "Construct a KiminaProver front. TODO."
  [_opts]
  (throw (ex-info "TODO: Kimina-Prover client" {:adapter :kimina-prover})))
