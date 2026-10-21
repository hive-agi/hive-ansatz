(ns hive-ansatz.assay.nested-list-test
  "ASSAY-LOOP-T3b: structural recursion over (List (List Nat)).

   Upstream defect (ansatz <= 0.2.79, still on main): the surface `match`
   compiler (ansatz.surface.match/count-ih-args and the rec-index scans in
   build-minor-premise) calls a constructor field recursive when the HEAD
   CONSTANT of its type is the inductive being matched. For `cons` of
   `List (List Nat)` the `head : List Nat` field also has head `List`, so the
   minor premise gets two IH binders where `List.rec` supplies one, and the
   kernel rejects the application with 'Type mismatch in application of
   List.rec'.

   These tests pin both the defect (so an upstream fix is noticed: flip the
   first test and drop the workaround note in hive-ansatz.assay.kernel) and
   the documented workaround: eliminate with `List.rec` directly. They run
   only when ansatz is on the classpath."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]
            [hive-ansatz.assay.certify :as certify]
            [hive-ansatz.ports :as ports]))

(def ^:private row-sum
  '(ansatz.core/defn hatest_rowsum [xs :- (List Nat)] Nat
     (match xs (List Nat) Nat (nil 0) (cons [hd tl] (+ hd (hatest_rowsum tl))))))

(def ^:private nested-match
  "Minimal repro: the head field is not even used."
  '(ansatz.core/defn hatest_rows_match [rows :- (List (List Nat))] Nat
     (match rows (List (List Nat)) Nat (nil 0) (cons [hd tl] (+ 1 (hatest_rows_match tl))))))

(def ^:private nested-rec
  "Workaround: the explicit eliminator, one IH per the real recursor."
  '(ansatz.core/defn hatest_rows_rec [rows :- (List (List Nat))] Nat
     (List.rec 0 (fn [hd :- (List Nat) tl :- (List (List Nat)) ih :- Nat]
                   (+ (hatest_rowsum hd) ih))
               rows)))

(deftest nested-list-match-upstream-defect
  (if-let [kp (certify/default-prover)]
    (let [r (ports/define! kp [nested-match])]
      (is (and (:error r) (str/includes? (:error r) "List.rec"))
          (str "upstream defect no longer reproduces; drop the workaround: " (pr-str r))))
    (is (nil? (certify/default-prover)) "ansatz absent: repro not exercised")))

(deftest nested-list-rec-workaround
  (if-let [kp (certify/default-prover)]
    (do
      (is (nil? (ports/define! kp [row-sum nested-rec])))
      (testing "the eliminator form reduces: kernel proves the true value, rejects a false one"
        (is (nil? (ports/prove! kp "hatest_rows_rec_ok" []
                                '(= Nat (hatest_rows_rec [[1 2] [3] []]) 6) '[(rfl)])))
        (is (some? (ports/prove! kp "hatest_rows_rec_bad" []
                                 '(= Nat (hatest_rows_rec [[1 2] [3] []]) 7) '[(rfl)])))))
    (is (nil? (certify/default-prover)) "ansatz absent: workaround not exercised")))
