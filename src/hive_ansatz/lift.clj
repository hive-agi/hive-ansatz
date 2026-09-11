(ns hive-ansatz.lift
  "Pure decisions of the lift pipeline: which decls a proof artifact carries,
   how it is addressed, and the report shapes. Effects live in
   hive-ansatz.certify."
  (:require [clojure.string :as str]
            [malli.core :as m]
            [hive-ansatz.schema :as schema])
  (:import [java.security MessageDigest]))

(defn content-address
  "`sha256:<hex>` over `bs`."
  [^bytes bs]
  (let [digest (.digest (MessageDigest/getInstance "SHA-256") bs)]
    (str "sha256:" (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))))

(m/=> content-address [:=> [:cat :any] schema/ContentAddress])

(defn artifact-file
  "File name an address is stored under inside a store directory."
  [address]
  (str (subs address (count "sha256:")) ".fressian"))

(m/=> artifact-file [:=> [:cat schema/ContentAddress] :string])

(defn select-names
  "Among `names` (an env overlay), the ones an artifact for the function
   `fn-name` proven by `theorem` must carry: the function, its generated
   lemmas (`f.…`) and the theorem itself."
  [names fn-name theorem]
  (let [f (str fn-name) t (str theorem) lemma (str f ".")]
    (into #{}
          (filter (fn [n] (or (= n f) (= n t) (str/starts-with? n lemma))))
          names)))

(m/=> select-names [:=> [:cat [:sequential :string] :symbol :symbol] [:set :string]])

(defn artifact-ref
  "The certificate-index reference for `obligation` discharged by the
   artifact at `address`."
  [obligation address]
  {:law-id   (:law-id obligation)
   :theorem  (get-in obligation [:property :theorem])
   :artifact address
   :prover   :ansatz})

(m/=> artifact-ref [:=> [:cat schema/Obligation schema/ContentAddress] schema/ArtifactRef])

(defn refused
  "A LiftReport naming why the lift was not proven."
  [reason details]
  {:proven? false :reasons [reason] :details details})

(m/=> refused [:=> [:cat schema/LiftReason [:map-of :keyword :any]] schema/LiftReport])

(defn proven
  "A LiftReport for an artifact stored at `path` carrying `names`."
  [ref path names]
  {:proven? true :reasons [] :artifact-ref ref :path path :names (vec (sort names))})

(m/=> proven [:=> [:cat schema/ArtifactRef :string [:set :string]] schema/LiftReport])
