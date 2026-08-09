(ns hive-ansatz.adapters.compile-targets
  "Concrete ICompileTarget adapters for :jvm, :cljw, :cljrs. Boundary:
   compile-kernel/run-probe spawn processes / load native artifacts and are
   scaffolded (TODO). target-key is data-derived and live."
  (:require [hive-ansatz.compile.target :as tgt]))

(defrecord JvmTarget []
  tgt/ICompileTarget
  (target-key [_] :jvm)
  (compile-kernel [_ _kernel] (throw (ex-info "TODO: jvm reference compile" {:target :jvm})))
  (run-probe [_ _artifact _probe] (throw (ex-info "TODO: jvm run-probe" {:target :jvm}))))

(defrecord CljwTarget []
  tgt/ICompileTarget
  (target-key [_] :cljw)
  (compile-kernel [_ _kernel] (throw (ex-info "TODO: cljw zig cross-compile" {:target :cljw})))
  (run-probe [_ _artifact _probe] (throw (ex-info "TODO: cljw run-probe" {:target :cljw}))))

(defrecord CljrsTarget []
  tgt/ICompileTarget
  (target-key [_] :cljrs)
  (compile-kernel [_ _kernel] (throw (ex-info "TODO: cljrs AOT native-lib (zero-interpreted-fallback)" {:target :cljrs})))
  (run-probe [_ _artifact _probe] (throw (ex-info "TODO: cljrs run-probe" {:target :cljrs}))))

(defn jvm-target [] (->JvmTarget))
(defn cljw-target [] (->CljwTarget))
(defn cljrs-target [] (->CljrsTarget))
