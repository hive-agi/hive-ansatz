(ns hive-ansatz.assay.certify
  "Boundary: discharge structural assay claims into Certificates.

   Tier ladder, strongest first, and the rule that picks the rung:
     :proof     an IProver kernel accepted EVERY obligation's decider equation
     :computed  the host decided the claim exhaustively over the finite data;
                used when no prover is injected, the checkers do not elaborate,
                or the kernel disagrees with the host (reported, never hidden)
     :property  the claim quantifies over an open domain the certifier can only
                sample (prompt independence of an opaque render fn)
   A certificate never states that an arm is better: that claim is
   statistical and stays in hive-assay."
  (:require [malli.core :as m]
            [hive-dsl.result :as r]
            [hive-ansatz.assay.claims :as claims]
            [hive-ansatz.assay.kernel :as k]
            [hive-ansatz.ports :as ports]
            [hive-ansatz.schema :as schema]))

(defn default-prover
  "The live-kernel IProver, or nil when ansatz is not on the classpath."
  []
  (try ((requiring-resolve 'hive-ansatz.adapters.kernel-prover/kernel-prover))
       (catch Throwable _ nil)))

(defn- computed-evidence [obligations status]
  (mapv (fn [o] {:obligation/id (:obligation/id o)
                 :expected (:obligation/expected o)
                 :status status})
        obligations))

(defn- discharge
  "Obligations -> {:tier :evidence :degraded?} through `prover` (may be nil)."
  [prover obligations]
  (if (nil? prover)
    {:tier :computed :evidence (computed-evidence obligations :computed)
     :degraded {:reason :no-kernel}}
    (if-let [{msg :error} (ports/define! prover k/checker-forms)]
      {:tier :computed :evidence (computed-evidence obligations :computed)
       :degraded {:reason :define-failed :message msg}}
      (let [ev (mapv (fn [{:obligation/keys [id theorem prop expected]}]
                       (let [msg (ports/prove! prover theorem [] prop k/tactics)]
                         (cond-> {:obligation/id id :expected expected
                                  :theorem theorem
                                  :status (if msg :rejected :proven)}
                           msg (assoc :message msg))))
                     obligations)
            rejected (filterv #(= :rejected (:status %)) ev)]
        (if (seq rejected)
          {:tier :computed :evidence ev
           :degraded {:reason :kernel-rejected :obligations (mapv :obligation/id rejected)}}
          {:tier :proof :evidence ev})))))

(defn- certificate [claim subject obligations {:keys [tier evidence degraded]}]
  (cond-> {:certificate/claim claim
           :certificate/holds? (every? :obligation/expected obligations)
           :certificate/tier tier
           :certificate/subject subject
           :certificate/evidence evidence}
    degraded (assoc :certificate/degraded degraded)))

(defn- certify-obligations [prover result]
  (r/map-ok result
            (fn [{:keys [claim facts obligations]}]
              (certificate claim facts obligations (discharge prover obligations)))))

(defn certify-balance
  "Result<Certificate> for :assay/balanced-design (claim a). `prover` may be
   nil (host-computed tier)."
  [prover design]
  (certify-obligations prover (claims/balance-obligations design)))

(defn certify-arm
  "Result<Certificate> for :assay/arm-invariants (claim c): the promoted
   `arm` satisfies every declared invariant. `incumbent` may be nil unless a
   :within-budget invariant is declared."
  [prover factors arm incumbent invariants]
  (certify-obligations prover (claims/invariant-obligations factors arm incumbent invariants)))

(defn- alias-ids [arm arms samples]
  (->> (concat (map :arm/id arms)
               (map #(str "hacert-alias-" % "-" (hash (:arm/id arm))) (range samples)))
       (remove #{(:arm/id arm)})
       distinct))

(defn certify-prompt-independence
  "Result<Certificate> for :assay/prompt-arm-independent (claim b): for each
   arm, `render` (arm -> prompt) returns the same prompt when only :arm/id is
   replaced by every other arm's id and `samples` fresh ids. `render` is
   opaque code, so this is sampled: tier :property, never :proof."
  ([render arms] (certify-prompt-independence render arms 8))
  ([render arms samples]
   (r/let-ok [checks (r/try-effect*
                      :assay/render-failed
                      (mapv (fn [arm]
                              (let [base (render arm)
                                    leaks (filterv #(not= base (render (assoc arm :arm/id %)))
                                                   (alias-ids arm arms samples))]
                                {:obligation/id (keyword "arm" (:arm/id arm))
                                 :expected (empty? leaks)
                                 :status :sampled
                                 :aliases-tried (count (alias-ids arm arms samples))
                                 :leaking-ids (vec (take 3 leaks))}))
                            arms))]
     (r/ok {:certificate/claim :assay/prompt-arm-independent
            :certificate/holds? (every? :expected checks)
            :certificate/tier :property
            :certificate/subject {:arms (count arms) :samples samples}
            :certificate/evidence checks}))))

(m/=> certify-balance [:=> [:cat :any schema/AssayDesign] :map])
