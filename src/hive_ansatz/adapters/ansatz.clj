(ns hive-ansatz.adapters.ansatz
  "Concrete ports for a live replikativ/ansatz environment.

   ONLY this namespace touches ansatz classes — load it under the :ansatz
   alias (or with ansatz otherwise on the classpath). Core namespaces stay
   host-neutral.

   LiveAnsatzEnv wraps the global a/ansatz-env atom: overlay reads the
   kernel Env's local constant map; add-decl! swaps an immutable
   addConstant result back into the atom; verify delegates to the kernel
   checker. AnsatzFressianCodec serializes opaque ConstantInfos with
   ansatz.export.storage's element handlers."
  (:require [clojure.data.fressian :as fress]
            [clojure.java.io :as io]
            [hive-ansatz.ports :as ports]
            [ansatz.core :as a]
            [ansatz.kernel.env :as env]
            [ansatz.kernel.name :as nm]
            [ansatz.export.storage]))

(def ^:private write-handlers @#'ansatz.export.storage/ansatz-element-write-handlers)
(def ^:private read-handlers @#'ansatz.export.storage/ansatz-element-read-handlers)

(defn- locals-field [e]
  (let [f (doto (.getDeclaredField (class e) "locals") (.setAccessible true))]
    (.get f e)))

(def ^:private thm-tag 2)

(defrecord LiveAnsatzEnv []
  ports/IProofEnv
  (overlay [_]
    (vec (vals (locals-field (a/env)))))
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
    (let [file (io/file path)]
      (io/make-parents file)
      (with-open [out (io/output-stream file)]
        (let [w (fress/create-writer out
                  :handlers (-> (merge fress/clojure-write-handlers write-handlers)
                                fress/associative-lookup
                                fress/inheritance-lookup))]
          (fress/write-object w {:format :ansatz-decls :version 1})
          (fress/write-object w (mapv #(str (.name %)) decls))
          (doseq [ci decls] (fress/write-object w ci))))
      (count decls)))
  (read-decls [_ path]
    (with-open [in (io/input-stream (io/file path))]
      (let [r (fress/create-reader in
                :handlers (fress/associative-lookup
                            (merge fress/clojure-read-handlers read-handlers)))
            header (fress/read-object r)
            names (fress/read-object r)
            decls (mapv (fn [_] (fress/read-object r)) names)]
        {:header header :decls decls}))))

(defn live-env [] (->LiveAnsatzEnv))
(defn fressian-codec [] (->AnsatzFressianCodec))
