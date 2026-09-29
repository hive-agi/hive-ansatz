(ns hive-ansatz.assay.kernel
  "Kernel reflection vocabulary for assay certificates, as DATA.

   `checker-forms` are the ansatz surface definitions of small total Bool/Nat
   deciders over flat `List Nat` encodings; the prop builders state
   `(= Bool <decider applied to literals> <expected>)`, which the kernel closes
   by `rfl` exactly when the decider reduces to the expected value. Nothing
   here touches a kernel: an IProver evaluates the forms (DIP)."
  (:require [malli.core :as m]))

(def prefix
  "Name prefix every checker and certificate theorem carries."
  "hacert_")

(def checker-forms
  "Checker definitions in dependency order. Structural recursion only, so
   each one elaborates without a termination argument."
  '[(ansatz.core/defn hacert_and [a :- Bool, b :- Bool] Bool
      (match a Bool Bool (false false) (true b)))
    (ansatz.core/defn hacert_not [a :- Bool] Bool
      (match a Bool Bool (false true) (true false)))
    (ansatz.core/defn hacert_implies [a :- Bool, b :- Bool] Bool
      (match a Bool Bool (false true) (true b)))
    (ansatz.core/defn hacert_b2n [a :- Bool] Nat
      (match a Bool Nat (false 0) (true 1)))
    (ansatz.core/defn hacert_len [xs :- (List Nat)] Nat
      (match xs (List Nat) Nat (nil 0) (cons [hd tl] (+ 1 (hacert_len tl)))))
    (ansatz.core/defn hacert_nth [n :- Nat, xs :- (List Nat)] Nat
      (match xs (List Nat) Nat (nil 0)
             (cons [hd tl] (match n Nat Nat (zero hd) (succ [k] (hacert_nth k tl))))))
    (ansatz.core/defn hacert_cnt [c :- Nat, xs :- (List Nat)] Nat
      (match xs (List Nat) Nat (nil 0)
             (cons [hd tl] (+ (hacert_b2n (Nat.beq hd c)) (hacert_cnt c tl)))))
    (ansatz.core/defn hacert_every [n :- Nat, r :- Nat, xs :- (List Nat)] Bool
      (match n Nat Bool (zero true)
             (succ [k] (hacert_and (Nat.beq (hacert_cnt k xs) r) (hacert_every k r xs)))))
    (ansatz.core/defn hacert_balanced [n :- Nat, r :- Nat, xs :- (List Nat)] Bool
      (hacert_and (Nat.ble 1 r)
                  (hacert_and (Nat.beq (hacert_len xs) (* r n))
                              (hacert_every n r xs))))
    (ansatz.core/defn hacert_inrange [rs :- (List Nat), xs :- (List Nat)] Bool
      (match rs (List Nat) Bool
             (nil (match xs (List Nat) Bool (nil true) (cons [h t] false)))
             (cons [rh rt] (match xs (List Nat) Bool (nil false)
                                  (cons [h t] (hacert_and (Nat.blt h rh) (hacert_inrange rt t)))))))
    (ansatz.core/defn hacert_diff [xs :- (List Nat), ys :- (List Nat)] Nat
      (match xs (List Nat) Nat (nil 0)
             (cons [hd tl] (match ys (List Nat) Nat (nil 0)
                                  (cons [h t] (+ (hacert_b2n (hacert_not (Nat.beq hd h)))
                                                 (hacert_diff tl t)))))))])

(defn checker-name
  "The kernel constant a checker form defines."
  [form]
  (str (second form)))

(defn- nat-list [xs] (vec xs))

(defn- bool-prop [expr expected]
  (list '= 'Bool expr expected))

(defn balanced-expr
  "Decider: every code in [0, n) occurs exactly r >= 1 times and nothing else
   occurs."
  [n r codes]
  (list 'hacert_balanced n r (nat-list codes)))

(defn in-range-expr
  "Decider: `idxs` has one index per radix, each below its radix."
  [radices idxs]
  (list 'hacert_inrange (nat-list radices) (nat-list idxs)))

(defn implies-expr
  "Decider: idxs[i] = a implies idxs[j] = b (negated consequent when `negate?`)."
  [idxs i a j b negate?]
  (let [consequent (list 'Nat.beq (list 'hacert_nth j (nat-list idxs)) b)]
    (list 'hacert_implies
          (list 'Nat.beq (list 'hacert_nth i (nat-list idxs)) a)
          (if negate? (list 'hacert_not consequent) consequent))))

(defn within-expr
  "Decider: `idxs` and `incumbent` differ in at most k positions."
  [idxs incumbent k]
  (list 'Nat.ble (list 'hacert_diff (nat-list idxs) (nat-list incumbent)) k))

(defn prop
  "The closed Prop stating that `expr` reduces to `expected` (a boolean)."
  [expr expected]
  (bool-prop expr expected))

(def tactics
  "Every certificate Prop is closed and decidable by evaluation."
  '[(rfl)])

(m/=> balanced-expr [:=> [:cat nat-int? nat-int? [:sequential nat-int?]] seq?])
(m/=> in-range-expr [:=> [:cat [:sequential nat-int?] [:sequential nat-int?]] seq?])
(m/=> within-expr [:=> [:cat [:sequential nat-int?] [:sequential nat-int?] nat-int?] seq?])
