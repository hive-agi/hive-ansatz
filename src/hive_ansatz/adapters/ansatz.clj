(ns hive-ansatz.adapters.ansatz
  "Concrete ports for a live replikativ/ansatz environment.

   ONLY this namespace touches ansatz classes; it also needs hive-fressian
   on the classpath (both arrive via local.deps.edn during dev). Core
   namespaces stay host-neutral.

   LiveAnsatzEnv wraps the global a/ansatz-env atom: overlay reads the
   kernel Env's local constant map; add-decl! swaps an immutable
   addConstant result back into the atom; verify delegates to the kernel
   checker. AnsatzFressianCodec registers the ansatz kernel-type handler
   domain into hive-fressian and speaks its envelope format."
  (:require [hive-ansatz.ports :as ports]
            [hive-fressian.codec :as codec]
            [hive-fressian.registry :as registry]
            [ansatz.core :as a]
            [ansatz.kernel.env :as env]
            [ansatz.kernel.name :as nm]
            [ansatz.export.storage]))

(registry/register! :ansatz/kernel
  {:write-handlers @#'ansatz.export.storage/ansatz-element-write-handlers
   :read-handlers @#'ansatz.export.storage/ansatz-element-read-handlers})

(def ^:private thm-tag 2)

(defrecord LiveAnsatzEnv []
  ports/IProofEnv
  (overlay [_]
    (vec (.allConstants (a/env))))
  (present? [_ decl-name]
    (some? (env/lookup (a/env) (nm/from-string decl-name))))
  (add-decl! [_ decl]
    (reset! a/ansatz-env (.addConstant (a/env) decl))
    nil)
  (verify [_ decl]
    (boolean (env/verifies? (a/env) (.type decl) (.value decl))))

  ports/IDeclInfo
  (decl-name [_ decl] (str (.name decl)))
  (theorem? [_ decl] (= thm-tag (int (.tag decl)))))

(defrecord AnsatzFressianCodec []
  ports/IDeclCodec
  (write-decls! [_ path decls]
    (:count (codec/write-envelope! path {:format :ansatz-decls} decls)))
  (read-decls [_ path]
    (let [{:keys [header items]} (codec/read-envelope path)]
      {:header header :decls items})))

(defn live-env [] (->LiveAnsatzEnv))
(defn fressian-codec [] (->AnsatzFressianCodec))
