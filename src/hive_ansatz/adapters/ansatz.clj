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
            [ansatz.export.storage]
            [hive-schemas.proven :as proven]))

(registry/register! :ansatz/kernel
  {:write-handlers @#'ansatz.export.storage/ansatz-element-write-handlers
   :read-handlers @#'ansatz.export.storage/ansatz-element-read-handlers})

(def ^:private thm-tag 2)

(defonce ^:private booted
  (delay (binding [a/*verbose* false]
           (when (or (nil? @a/ansatz-env)
                     (nil? (env/lookup (a/env) (nm/from-string "Nat"))))
             (a/load-init!)))))

(defn- kernel-env
  "The live kernel Env, booting the stdlib once when nothing has."
  []
  @booted
  (a/env))

(def ^:private lifted-ns
  "The namespace lifted forms are evaluated in, so the `m/=>` registration
   and the `a/defn` that consults it key on the same name."
  (let [n (create-ns 'hive-ansatz.lifted)]
    (binding [*ns* n] (refer 'clojure.core))
    n))

(defrecord LiveAnsatzEnv []
  ports/IProofEnv
  (overlay [_]
    (vec (.allConstants (kernel-env))))
  (present? [_ decl-name]
    (some? (env/lookup (kernel-env) (nm/from-string decl-name))))
  (add-decl! [_ decl]
    (reset! a/ansatz-env (.addConstant (kernel-env) decl))
    nil)
  (verify [_ decl]
    (boolean (env/verifies? (kernel-env) (.type decl) (.value decl))))

  ports/IDeclInfo
  (decl-name [_ decl] (str (.name decl)))
  (theorem? [_ decl] (= thm-tag (int (.tag decl))))

  ports/IProver
  (define! [_ forms]
    (kernel-env)
    (try
      (binding [a/*verbose* false *ns* lifted-ns]
        (doseq [f forms] (eval f)))
      nil
      (catch Exception e {:error (.getMessage e)})))
  (prove! [_ theorem params prop tactics]
    (kernel-env)
    (proven/proof-failure theorem params prop tactics)))

(defrecord AnsatzFressianCodec []
  ports/IDeclCodec
  (write-decls! [_ path decls]
    (:count (codec/write-envelope! path {:format :ansatz-decls} decls)))
  (read-decls [_ path]
    (let [{:keys [header items]} (codec/read-envelope path)]
      {:header header :decls items})))

(defn live-env [] (->LiveAnsatzEnv))
(defn fressian-codec [] (->AnsatzFressianCodec))
