(ns hive-ansatz.lean.toolchain
  "Gated Lean toolchain lifecycle. The warm-boot GATE is a pure decision
   (mirrors the mathlib :7899 async warm-boot gate); one-time toolchain setup
   and warm-boot are effectful boundary steps, scaffolded (TODO). Default off:
   HIVE_ANSATZ_LEAN unset/0 ⇒ :disabled — nothing installs or boots on a
   normal hive start."
  (:require [malli.core :as m]
            [hive-ansatz.schema :as schema]))

(def min-disk-gb
  "Provisional free-disk floor for a Lean+mathlib4 build (GB); pending
   empirical grounding."
  12)

(def min-ram-gb
  "Provisional free-RAM floor for a Lean REPL warm-boot (GB); pending
   empirical grounding."
  6)

(defn boot-decision
  "Pure gate: given a LeanBootProbe, rule whether to warm-boot a Lean env."
  [{:keys [enabled? toolchain-present? free-disk-gb free-ram-gb]}]
  (cond
    (not enabled?)               {:boot? false :reason :disabled}
    (not toolchain-present?)     {:boot? false :reason :no-toolchain}
    (< free-disk-gb min-disk-gb) {:boot? false :reason :insufficient-disk}
    (< free-ram-gb min-ram-gb)   {:boot? false :reason :insufficient-ram}
    :else                        {:boot? true  :reason :ok}))

(m/=> boot-decision [:=> [:cat schema/LeanBootProbe] schema/LeanBootDecision])

(defn ensure-toolchain!
  "Idempotent one-time Lean toolchain setup (elan/lake + `lake build repl`).
   TODO — never runs on a default hive start."
  [_opts]
  (throw (ex-info "TODO: idempotent elan/lake + lake build repl" {:step :ensure-toolchain})))

(defn warm-boot!
  "Gated async warm-boot of a Lean IProofEnv (mirrors mathlib :7899). Consults
   boot-decision; TODO."
  [_opts]
  (throw (ex-info "TODO: gated Lean warm-boot" {:step :warm-boot})))
