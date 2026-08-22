# hive-ansatz

<!-- hive-badges -->

[![Clojars Project](https://img.shields.io/clojars/v/io.github.hive-agi/hive-ansatz.svg)](https://clojars.org/io.github.hive-agi/hive-ansatz)
[![cljdoc](https://cljdoc.org/badge/io.github.hive-agi/hive-ansatz)](https://cljdoc.org/d/io.github.hive-agi/hive-ansatz/CURRENT)
[![release](https://github.com/hive-agi/hive-ansatz/actions/workflows/release.yml/badge.svg)](https://github.com/hive-agi/hive-ansatz/actions/workflows/release.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)

<!-- /hive-badges -->

Prover-agnostic proof persistence for [ansatz](https://github.com/replikativ/ansatz)-style
kernel environments, packaged as a host-neutral
[hive-addon](https://github.com/hive-agi/hive-addon) (`IAddon` 0.2.0).

## What it does

- **Export** a live prover env's *local overlay* (session-proved defns,
  equation lemmas, theorems with proof terms) to a durable snapshot file.
- **Import** a snapshot into an env, then **kernel-re-verify every theorem** —
  a snapshot is only trusted after `:all-verified true`.
- **Inspect** snapshots without touching the env.
- **Proving idioms as data** (`hive-ansatz.recipes`) — deterministic,
  simp-free tactic strategies for large stores, queryable by tag.

## Architecture (CPPB / SOLID)

| layer | ns | role |
|---|---|---|
| types | `hive-ansatz.schema` | malli value objects (drive `m/=>` + synthesized tests) |
| ports | `hive-ansatz.ports` | `IProofEnv` / `IDeclInfo` / `IDeclCodec` (ISP; decls stay opaque) |
| pure | `hive-ansatz.snapshot` | selection + import-plan decisions |
| pure | `hive-ansatz.recipes` | idioms as data (OCP: new idiom = new entry) |
| boundary | `hive-ansatz.persistence` | `export!` / `import!` / `inspect` over injected ports (DIP) |
| adapter | `hive-ansatz.adapters.ansatz` | `LiveAnsatzEnv` + `AnsatzFressianCodec` (only ns touching ansatz classes; codec delegates to [hive-fressian](../hive-fressian)) |
| addon | `hive-ansatz.addon` | `IAddon` record; `ansatz_proofs` supertool |

Core namespaces never require ansatz or hive-fressian — the adapter loads
only when both are on the classpath. Without a prover the addon initializes
degraded with the recipes surface still available.

`deps.edn` is `:mvn/version`-only. Unpublished deps (ansatz, hive-fressian
pre-publish) are supplied by the untracked `local.deps.edn`:

```bash
clj -Sdeps "$(cat local.deps.edn)" -M:test ...
```

## Usage

```clojure
;; with ansatz on the classpath and a live env
(require '[hive-ansatz.adapters.ansatz :as ad]
         '[hive-ansatz.persistence :as p])

(def env (ad/live-env))
(def codec (ad/fressian-codec))

(p/export! env env codec "exports/session.fressian" {:exclude #{"scratch"}})
(p/import! env env codec "exports/session.fressian")
;; => {:added [..] :skipped [..] :all-verified true :results {..}}
```

## Tests

```bash
clj -M:test -e "(require 'hive-ansatz.snapshot-test 'hive-ansatz.recipes-test)(let [r (clojure.test/run-all-tests #\"hive-ansatz.*-test\")](shutdown-agents)(System/exit (+ (:fail r)(:error r))))"
```
