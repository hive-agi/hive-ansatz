(ns hive-ansatz.lift-test
  "Trifecta + contracts for the pure lift decisions and the certify boundary
   (DIP: exercised through stub ports, never through a kernel)."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [malli.core :as m]
            [hive-schemas.test :as hst]
            [hive-ansatz.certify :as certify]
            [hive-ansatz.lift :as lift]
            [hive-ansatz.ports :as ports]
            [hive-ansatz.schema :as schema])
  (:import [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]))

;; ============================================================================
;; Schema-synthesized
;; ============================================================================

(hst/deftrifecta-from-schema artifact-ref-conforms
  hive-ansatz.lift/artifact-ref
  {:in  [:cat schema/Obligation schema/ContentAddress]
   :out schema/ArtifactRef
   :rel (fn [[ob address] out]
          (and (= (:law-id ob) (:law-id out))
               (= (get-in ob [:property :theorem]) (:theorem out))
               (= address (:artifact out))
               (= :ansatz (:prover out))))
   :num-tests 100})

(hst/deftrifecta-from-schema proven-conforms
  hive-ansatz.lift/proven
  {:in  [:cat schema/ArtifactRef :string [:set schema/DeclName]]
   :out schema/LiftReport
   :rel (fn [[ref path names] out]
          (and (:proven? out)
               (empty? (:reasons out))
               (= ref (:artifact-ref out))
               (= path (:path out))
               (= (sort names) (:names out))))
   :num-tests 100})

;; ============================================================================
;; Hand-written: what a shape cannot state
;; ============================================================================

(deftest content-address-is-sha256-over-the-bytes
  (is (= "sha256:e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
         (lift/content-address (byte-array 0))))
  (is (= "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
         (lift/content-address (.getBytes "abc" "UTF-8")))))

(deftest select-names-keeps-the-function-its-lemmas-and-the-theorem
  (is (= #{"add" "add.eq_1" "add.eq_2" "thm"}
         (lift/select-names ["add" "add.eq_1" "add.eq_2" "thm" "adder" "scratch" "thm.x"]
                            'add 'thm)))
  (is (= #{} (lift/select-names [] 'add 'thm))))

;; ============================================================================
;; Stub ports
;; ============================================================================

(defrecord Decl [name theorem?])

(defrecord StubEnv [decls]
  ports/IProofEnv
  (overlay [_] @decls)
  (present? [_ n] (boolean (some #(= n (:name %)) @decls)))
  (add-decl! [_ d]
    (swap! decls (fn [ds] (conj (vec (remove #(= (:name %) (:name d)) ds)) d)))
    nil)
  (verify [_ _] true)
  ports/IDeclInfo
  (decl-name [_ d] (:name d))
  (theorem? [_ d] (:theorem? d)))

(defrecord AcceptingProver [env defined]
  ports/IProver
  (define! [_ forms]
    (swap! defined conj forms)
    (doseq [f forms :when (= 'ansatz.core/defn (first f))]
      (ports/add-decl! env (->Decl (str (second f)) false))
      (ports/add-decl! env (->Decl (str (second f) ".eq_1") false)))
    nil)
  (prove! [_ theorem _ _ _]
    (ports/add-decl! env (->Decl (str theorem) true))
    nil))

(defrecord RejectingProver [message]
  ports/IProver
  (define! [_ _] nil)
  (prove! [_ _ _ _ _] message))

(defrecord FailingDefine [message]
  ports/IProver
  (define! [_ _] {:error message})
  (prove! [_ _ _ _ _] nil))

(defrecord EdnCodec []
  ports/IDeclCodec
  (write-decls! [_ path decls]
    (spit path (pr-str (mapv #(select-keys % [:name :theorem?]) decls)))
    (count decls))
  (read-decls [_ path]
    {:header {} :decls (mapv map->Decl (read-string (slurp path)))}))

(def ^:private the-lift
  {:name  'add
   :forms '[(malli.core/=> add [:=> [:cat :int :int] :int])
            (ansatz.core/defn add [a b] (+ a b))]})

(def ^:private the-obligation
  {:law-id   :foreign/go.store.Add
   :property {:theorem 'add-right-id
              :params  '[x :- Nat]
              :prop    '(= Nat ((add x) 0) x)
              :tactics '[(induction x) (all_goals (simp_all [add]))]}})

(defn- temp-store []
  (str (Files/createTempDirectory "lift-store" (make-array FileAttribute 0))))

(defn- ports-over [env prover store]
  {:prover prover :env env :info env :codec (->EdnCodec) :store store})

(defn- stored-files [store]
  (vec (.listFiles (io/file store))))

;; ============================================================================
;; The boundary
;; ============================================================================

(deftest a-proven-lift-is-stored-content-addressed
  (let [env    (->StubEnv (atom [(->Decl "scratch" false)]))
        prover (->AcceptingProver env (atom []))
        store  (temp-store)
        report (certify/prove-lift! (ports-over env prover store) the-lift the-obligation)]
    (is (m/validate schema/LiftReport report) (pr-str (m/explain schema/LiftReport report)))
    (is (:proven? report))
    (is (= #{"add" "add.eq_1" "add-right-id"} (set (:names report))))
    (testing "the file is named by its content address"
      (let [address (get-in report [:artifact-ref :artifact])
            file    (io/file (:path report))]
        (is (.exists file))
        (is (= (lift/artifact-file address) (.getName file)))
        (is (= address (lift/content-address (Files/readAllBytes (.toPath file)))))))
    (testing "the artifact carries exactly the selected decls, not the rest of the overlay"
      (is (= #{"add" "add.eq_1" "add-right-id"}
             (set (map :name (:decls (ports/read-decls (->EdnCodec) (:path report))))))))
    (testing "the reference is what a certificate index consumes"
      (is (= {:law-id :foreign/go.store.Add :theorem 'add-right-id :prover :ansatz}
             (dissoc (:artifact-ref report) :artifact))))
    (testing "proving again defines nothing twice and lands on the same address"
      (let [again (certify/prove-lift! (ports-over env prover store) the-lift the-obligation)]
        (is (= 1 (count @(:defined prover))))
        (is (= (:artifact-ref report) (:artifact-ref again)))
        (is (= 1 (count (stored-files store))))))))

(deftest a-rejected-proof-stores-nothing-and-names-the-reason
  (let [env    (->StubEnv (atom []))
        store  (temp-store)
        report (certify/prove-lift! (ports-over env (->RejectingProver "proof rejected: goal remains") store)
                                    the-lift the-obligation)]
    (is (= {:proven? false
            :reasons [:kernel-rejected]
            :details {:message "proof rejected: goal remains"}}
           report))
    (is (empty? (stored-files store)))))

(deftest a-form-that-does-not-elaborate-is-refused-before-any-proof
  (let [env    (->StubEnv (atom []))
        store  (temp-store)
        report (certify/prove-lift! (ports-over env (->FailingDefine "unknown constant") store)
                                    the-lift the-obligation)]
    (is (= [:define-failed] (:reasons report)))
    (is (= "unknown constant" (get-in report [:details :message])))
    (is (empty? (stored-files store)))))
