(ns hive-ansatz.recipes
  "Proving strategies as DATA (laws-as-data lineage): each idiom is an
   addressable map conforming to hive-ansatz.schema/Idiom. New idiom =
   new entry, no code change (OCP). Query with find-idioms.

   These are prover-generic tactic idioms for deterministic (simp-free)
   proving over large stores; domain-specific proof content does NOT
   belong here."
  (:require [clojure.set :as set]
            [malli.core :as m]
            [hive-ansatz.schema :as schema]))

(def idioms
  [{:id :equation-lemmas
    :title "Drive rewriting with auto-generated equation lemmas"
    :rule "A recursive defn generates f.eq_N per match LEAF (multi-level match: one equation per leaf, numbered in leaf order). Fetch the exact statement from the env before writing the rewrite chain."
    :tags #{:rewrite :defn}}

   {:id :no-simp-on-large-store
    :title "Avoid simp/simp_all over large stores"
    :rule "simp and simp_all loop pathologically over mathlib-scale stores even with explicit lemma lists. Drive proofs deterministically with rewrite/cases/dsimp/exact."
    :tags #{:simp :performance}}

   {:id :rewrite-rotates
    :title "rewrite targets only the goal and rotates the goal list"
    :rule "After a rewrite, the rewritten goal moves to the back of the goal list. Flat multi-goal tactic chains then hit the wrong branch."
    :fix "Collapse to a single goal before chaining rewrites; close side goals first with all_goals+try passes."
    :tags #{:rewrite :goals}}

   {:id :rewrite-in-try-rollback
    :title "rewrite + dependent tactic inside one try block rolls back silently"
    :rule "(try (rewrite X) (exact Y)) under all_goals fails silently: the rewrite rotates the goal list, the exact targets the wrong goal, and the whole try block rolls back."
    :fix "Split into separate passes: (all_goals (try (rewrite X))) then (all_goals (try (exact Y)))."
    :tags #{:rewrite :goals :try}}

   {:id :unfold-nonrecursive
    :title "unfold delta-expands non-recursive defns"
    :rule "Non-recursive defns may generate no (or partial) equation lemmas. unfold expands them in the goal, leaving beta-redexes that dsimp cleans."
    :tags #{:unfold :defn}}

   {:id :have-defeq-bridge
    :title "have bridges surface-form store lemmas via kernel defeq"
    :rule "A have with a raw-constant type and a proof term stated in instance/surface form (HMod.hMod, HMul.hMul, ...) typechecks — the kernel defeq-check unfolds instances. Restate store lemmas in raw form for syntactic rewriting."
    :tags #{:have :defeq :mathlib}}

   {:id :dsimp-closes-refl
    :title "Close refl-shaped leaves with dsimp, not rfl"
    :rule "After induction, rfl can fail on literal-equality leaves with unsolved motive metavariables. dsimp closes goals that reduce to refl; a dsimp with no work errors, so guard mid-chain dsimps with try."
    :fix "End chains with (try (dsimp)); close base cases with exact on the equation lemma."
    :tags #{:dsimp :rfl :induction}}

   {:id :cases-on-term
    :title "cases on a Bool/inductive term substitutes in the goal only"
    :rule "(cases h <term>) binds h : <term> = <literal> and substitutes where the term appears SYNTACTICALLY in the goal; hypotheses are untouched."
    :tags #{:cases :goals}}

   {:id :import-hyp-via-backward-rewrite
    :title "Import a hypothesis without rewrite-at-hyp support"
    :rule "To use ha : f X = lit against the goal: case the goal's subject to reach a literal-vs-literal goal, (rewrite <- ha) to reintroduce f X in place of the literal, then collapse with equation lemmas + discriminant hypotheses."
    :tags #{:rewrite :hypotheses}}

   {:id :induction-skeleton
    :title "Robust induction skeleton"
    :rule "(induction x) (all_goals (intro h)) (all_goals (try (exact <base-eq-lemma>))) intros antecedents everywhere, closes base cases, and leaves the step case as the sole goal so rewrite rotation becomes a no-op."
    :tags #{:induction :goals}}

   {:id :have-term-form
    :title "Prefer 3-arg have with an explicit proof term"
    :rule "(have name TYPE proof-term) binds cleanly. The 2-arg subgoal form interacts badly with goal rotation."
    :tags #{:have :goals}}

   {:id :factor-step-through-helper
    :title "Factor a nested-match step through a named helper"
    :rule "When a recursive defn matches on its own recursive call, prove f (c x) = helper x (f (sub x)) once, then reason exclusively through the helper's equation lemmas and case splits."
    :tags #{:defn :rewrite :architecture}}])

(defn find-idioms
  "Idioms whose :tags intersect `tags` (a set of keywords). Empty/nil tags
   returns all idioms."
  [tags]
  (if (seq tags)
    (filterv (fn [{ts :tags}] (boolean (seq (set/intersection ts tags)))) idioms)
    idioms))

(m/=> find-idioms [:=> [:cat [:maybe [:set :keyword]]] [:vector schema/Idiom]])
