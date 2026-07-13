(ns hive-ansatz.persistence
  "Effectful boundary of proof persistence: run the pure snapshot plan
   against injected ports (IProofEnv + IDeclInfo + IDeclCodec).

   Trust model: an import is only trusted after :all-verified is true —
   every theorem read from disk is kernel-re-checked in the target env."
  (:require [hive-ansatz.ports :as ports]
            [hive-ansatz.snapshot :as snapshot]))

(defn- meta-of [info decl]
  {:name (ports/decl-name info decl)
   :theorem? (boolean (ports/theorem? info decl))})

(defn export!
  "Snapshot the env's local overlay (filtered by `spec`) to `path`.
   Returns an ExportReport."
  ([env info codec path] (export! env info codec path {}))
  ([env info codec path spec]
   (let [decls (ports/overlay env)
         by-name (into {} (map (fn [d] [(ports/decl-name info d) d])) decls)
         metas (snapshot/select (mapv #(meta-of info %) decls) spec)
         selected (mapv (comp by-name :name) metas)]
     (ports/write-decls! codec path selected)
     (snapshot/export-report path metas))))

(defn inspect
  "Read a snapshot without touching the env. Returns {:header .. :metas [..]}."
  [info codec path]
  (let [{:keys [header decls]} (ports/read-decls codec path)]
    {:header header
     :metas (mapv #(meta-of info %) decls)}))

(defn import!
  "Add absent decls from the snapshot at `path` into the env, then
   kernel-verify EVERY theorem in the snapshot. Returns an ImportReport."
  [env info codec path]
  (let [{:keys [decls]} (ports/read-decls codec path)
        metas (mapv #(meta-of info %) decls)
        plan (snapshot/import-plan metas #(ports/present? env %))
        to-add (set (:add plan))]
    (doseq [d decls
            :when (contains? to-add (ports/decl-name info d))]
      (ports/add-decl! env d))
    (let [results (into {}
                        (for [d decls
                              :when (ports/theorem? info d)]
                          [(ports/decl-name info d)
                           (boolean (ports/verify env d))]))]
      (snapshot/import-report plan results))))
