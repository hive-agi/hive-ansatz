(ns hive-ansatz.compile.driver
  "PURE lowering of a Kernel to a compilable unit and to the argv that builds it.

   The emitted unit bakes every probe in at COMPILE time and dispatches on an
   argv index, so an artifact never needs an interpreter to answer a probe.
   Effectful compilation and execution live in
   hive-ansatz.adapters.compile-targets."
  (:require [clojure.string :as str]
            [malli.core :as m]
            [hive-ansatz.schema :as schema]))

(def unknown-probe
  "What an artifact prints when handed an index it carries no probe for."
  :hive-ansatz/unknown-probe)

(defn driver-source
  "The `-main` answering probe i on stdout as one pr-str'd value.

   Dispatches with `cond` + `=` over the raw argv string, and reads argv from
   -main's rest arg: both are portability constraints of the native dialects,
   not style."
  [probes]
  (let [clauses (->> probes
                     (map-indexed (fn [i p] (str "(= i \"" i "\") " p)))
                     (str/join "\n                       "))]
    (str "(defn -main [& args]\n"
         "  (let [i (first args)]\n"
         "    (println (pr-str (cond "
         (when (seq probes) (str clauses "\n                       "))
         ":else " unknown-probe ")))))\n")))

(m/=> driver-source [:=> [:cat [:vector :string]] :string])

(defn unit-source
  "The complete compilable unit for `kernel` under `profile`."
  [profile kernel]
  (str (when (:unit-ns-form? profile) (str "(ns " (:ns kernel) ")\n\n"))
       (:source kernel)
       "\n"
       (driver-source (:probes kernel))))

(m/=> unit-source [:=> [:cat schema/CompileTarget schema/Kernel] :string])

(defn unit-file-name
  "Relative path the unit is written to. A unit that declares an `ns` must sit
   on that namespace's path, or the compiler's -m lookup will not find it."
  [profile kernel]
  (if (:unit-ns-form? profile)
    (str (-> (:ns kernel) (str/replace "." "/") (str/replace "-" "_")) ".clj")
    "unit.clj"))

(m/=> unit-file-name [:=> [:cat schema/CompileTarget schema/Kernel] :string])

(defn build-argv
  "Lower `profile`'s argv template against `ctx`, then append the flags its
   gates require. Keywords in :build-args are placeholders resolved from `ctx`.
   Throws when the profile carries no template, or when a placeholder is
   unbound, rather than guessing."
  [profile ctx]
  (when-not (:build-args profile)
    (throw (ex-info "compile target declares no :build-args"
                    {:target (:target profile)})))
  (into (mapv (fn [a]
                (if (keyword? a)
                  (or (get ctx a)
                      (throw (ex-info "unbound :build-args placeholder"
                                      {:placeholder a :target (:target profile)})))
                  a))
              (:build-args profile))
        (mapcat #(get (:gate-args profile) %) (sort (:gates profile)))))

(m/=> build-argv
      [:=> [:cat schema/CompileTarget [:map-of :keyword :string]] [:vector :string]])

(defn probe-index
  "Index of `probe` in `artifact`, or nil when the artifact carries no such
   probe. An arm may only be asked for a probe that was baked into it."
  [artifact probe]
  (first (keep-indexed #(when (= probe %2) %1) (:probes artifact))))

(m/=> probe-index [:=> [:cat schema/KernelArtifact :string] [:maybe :int]])
