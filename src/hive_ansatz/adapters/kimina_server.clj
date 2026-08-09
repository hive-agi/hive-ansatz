(ns hive-ansatz.adapters.kimina-server
  "IProofEnv/IDeclInfo over project-numina/kimina-lean-server (FastAPI wrapper
   round the Lean REPL, parallel checking at scale). Boundary: the HTTP client
   is scaffolded (TODO); only this namespace would speak HTTP."
  (:require [hive-ansatz.ports :as ports]))

(defrecord KiminaServerEnv [base-url pin]
  ports/IProofEnv
  (overlay [_] (throw (ex-info "TODO: kimina-server overlay" {:adapter :kimina-server})))
  (present? [_ _decl-name] (throw (ex-info "TODO: kimina-server present?" {:adapter :kimina-server})))
  (add-decl! [_ _decl] (throw (ex-info "TODO: kimina-server add-decl!" {:adapter :kimina-server})))
  (verify [_ _decl] (throw (ex-info "TODO: kimina-server verify" {:adapter :kimina-server})))

  ports/IDeclInfo
  (decl-name [_ _decl] (throw (ex-info "TODO: kimina-server decl-name" {:adapter :kimina-server})))
  (theorem? [_ _decl] (throw (ex-info "TODO: kimina-server theorem?" {:adapter :kimina-server}))))

(defn kimina-server-env
  "Construct a KiminaServerEnv against a kimina-lean-server base URL. TODO."
  [_opts]
  (throw (ex-info "TODO: HTTP client to kimina-lean-server" {:adapter :kimina-server})))
