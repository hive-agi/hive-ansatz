(ns hive-ansatz.compile.target
  "ICompileTarget port + provider-behaviour-as-DATA registry.

   The protocol's canonical home is hive-spi (shared: carto verify-ladder and
   hive-knowledge ICompileVerifier consume it — native-kernels P6); it is
   scaffolded here pending promotion. Concrete adapters live in
   hive-ansatz.adapters.compile-targets."
  (:require [malli.core :as m]
            [hive-ansatz.schema :as schema]))

(defprotocol ICompileTarget
  "Lower a proof-carrying kernel to a runtime artifact and run probes on it.
   Behaviour is data (see target-profiles); one generic driver reads a
   profile, so a new target is a data entry (OCP), not a new impl."
  (target-key [this]
    "The registry key this adapter serves (keyword).")
  (compile-kernel [this kernel]
    "Lower `kernel` to a runtime artifact; return an artifact handle.")
  (run-probe [this artifact probe]
    "Run `probe` against `artifact`; return a CheckResult."))

(def target-profiles
  "DIP swap point: compile-target behaviour as data, keyed by target."
  {:jvm   {:target :jvm   :dialect-key :clj  :opacity :none        :oracle? true  :artifact :classes               :gates #{}}
   :cljw  {:target :cljw  :dialect-key :clj  :opacity :bytecode    :oracle? false :artifact :self-contained-binary :gates #{}}
   :cljrs {:target :cljrs :dialect-key :rust :opacity :native-code :oracle? false :artifact :native-lib            :gates #{:zero-interpreted-fallback}}})

(defn profile
  "The CompileTarget profile registered for `k`, or nil."
  [k]
  (get target-profiles k))

(m/=> profile [:=> [:cat schema/CompileTargetKey] [:maybe schema/CompileTarget]])

(defn oracle-target
  "The single reference (oracle) target key."
  []
  (some (fn [[k p]] (when (:oracle? p) k)) target-profiles))

(defn add-profile
  "OCP: register a further target as data. Returns the extended registry."
  [profiles p]
  (assoc profiles (:target p) p))

(m/=> add-profile
      [:=> [:cat [:map-of schema/CompileTargetKey schema/CompileTarget] schema/CompileTarget]
       [:map-of schema/CompileTargetKey schema/CompileTarget]])
