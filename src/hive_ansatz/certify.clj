(ns hive-ansatz.certify
  "Effectful boundary of the lift pipeline: define a lifted foreign function
   in the kernel, prove the obligation's property, and persist the
   proof-carrying decls as a content-addressed Fressian artifact, over
   injected ports (IProver + IProofEnv + IDeclInfo + IDeclCodec)."
  (:require [clojure.java.io :as io]
            [malli.core :as m]
            [hive-ansatz.lift :as lift]
            [hive-ansatz.persistence :as persistence]
            [hive-ansatz.ports :as ports]
            [hive-ansatz.schema :as schema])
  (:import [java.io File]
           [java.nio.file Files StandardCopyOption]))

(defn- store-artifact!
  "Export the `include` decls to a temp file inside `store`, address the
   bytes and move the file to its content-addressed name. Returns
   [address path]."
  [env info codec store include]
  (let [dir (io/file store)
        _   (.mkdirs dir)
        tmp (File/createTempFile "lift-" ".tmp" dir)]
    (try
      (persistence/export! env info codec (.getPath tmp) {:include include})
      (let [address (lift/content-address (Files/readAllBytes (.toPath tmp)))
            dest    (io/file dir (lift/artifact-file address))]
        (Files/move (.toPath tmp) (.toPath dest)
                    (into-array [StandardCopyOption/REPLACE_EXISTING]))
        [address (.getPath dest)])
      (finally (.delete tmp)))))

(defn prove-lift!
  "Define `lift`'s forms through `prover` (skipped when the env already holds
   the function), prove `obligation`'s property, and when the kernel accepts
   it persist the function, its lemmas and the theorem under `store`.
   Returns a LiftReport; a refusal names its reason and stores nothing."
  [{:keys [prover env info codec store]} lift obligation]
  (let [{:keys [theorem params prop tactics]} (:property obligation)
        err (when-not (ports/present? env (str (:name lift)))
              (ports/define! prover (:forms lift)))]
    (if err
      (lift/refused :define-failed {:message (:error err)})
      (if-let [msg (ports/prove! prover theorem params prop tactics)]
        (lift/refused :kernel-rejected {:message msg})
        (let [names   (map #(ports/decl-name info %) (ports/overlay env))
              include (lift/select-names names (:name lift) theorem)
              [address path] (store-artifact! env info codec store include)]
          (lift/proven (lift/artifact-ref obligation address) path include))))))

(m/=> prove-lift! [:=> [:cat :map schema/Lift schema/Obligation] schema/LiftReport])
