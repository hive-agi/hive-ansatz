(ns hive-ansatz.adapters.lean-repl
  "IProofEnv/IDeclInfo over leanprover-community/repl (`lake exe repl`, JSON
   stdin/stdout, env-tracked). Boundary: process spawn + JSON I/O are
   scaffolded (TODO); only this namespace would touch the Lean process."
  (:require [hive-ansatz.ports :as ports]))

(defrecord LeanReplEnv [proc pin]
  ports/IProofEnv
  (overlay [_] (throw (ex-info "TODO: lean-repl overlay" {:adapter :lean-repl})))
  (present? [_ _decl-name] (throw (ex-info "TODO: lean-repl present?" {:adapter :lean-repl})))
  (add-decl! [_ _decl] (throw (ex-info "TODO: lean-repl add-decl!" {:adapter :lean-repl})))
  (verify [_ _decl] (throw (ex-info "TODO: lean-repl verify" {:adapter :lean-repl})))

  ports/IDeclInfo
  (decl-name [_ _decl] (throw (ex-info "TODO: lean-repl decl-name" {:adapter :lean-repl})))
  (theorem? [_ _decl] (throw (ex-info "TODO: lean-repl theorem?" {:adapter :lean-repl}))))

(defn lean-repl-env
  "Construct a LeanReplEnv over a `lake exe repl` process. TODO: spawn."
  [_opts]
  (throw (ex-info "TODO: spawn `lake exe repl` (JSON stdin/stdout)" {:adapter :lean-repl})))
