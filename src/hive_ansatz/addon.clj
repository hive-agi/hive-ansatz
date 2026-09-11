(ns hive-ansatz.addon
  "IAddon boundary for the hive-ansatz bounded context.

   Implements the standalone hive-addon contract directly. The prover env,
   decl introspection, and codec arrive as injected ports; when absent, the
   live-ansatz adapter is resolved lazily (requiring-resolve) so the addon
   initializes host-neutrally and degrades cleanly without ansatz on the
   classpath (the recipes surface stays available)."
  (:require [hive-addon.protocol :as addon]
            [hive-ansatz.persistence :as persistence]
            [hive-ansatz.recipes :as recipes]
            [clojure.edn :as edn]
            [hive-ansatz.certify :as certify]))

(def addon-id-value "hive.ansatz")

(defn- resolve-default-ports []
  (try
    (let [live-env (requiring-resolve 'hive-ansatz.adapters.ansatz/live-env)
          codec (requiring-resolve 'hive-ansatz.adapters.ansatz/fressian-codec)
          e (live-env)]
      {:env e :info e :codec (codec) :prover e})
    (catch Throwable t
      {:error (.getMessage t)})))

(defn- configured-ports [config]
  (let [env (:hive-ansatz/env config)
        info (or (:hive-ansatz/info config) env)
        codec (:hive-ansatz/codec config)
        prover (or (:hive-ansatz/prover config) env)]
    (if (and env codec)
      {:env env :info info :codec codec :prover prover}
      (resolve-default-ports))))

(defn- tool-parameter [parameters k]
  (or (get parameters k) (get parameters (name k))))

(defn- handle-proofs-tool [state parameters]
  (let [{:keys [env info codec prover]} @state
        command (some-> (tool-parameter parameters :command) name)
        path (tool-parameter parameters :path)
        exclude (some->> (tool-parameter parameters :exclude) (into #{}))
        tags (some->> (tool-parameter parameters :tags) (mapv keyword) (into #{}))
        read-edn (fn [k] (some-> (tool-parameter parameters k) edn/read-string))]
    (cond
      (= "idioms" command)
      {:success true :idioms (recipes/find-idioms tags)}

      (nil? env)
      {:success false :error "no prover env configured (ansatz not on classpath and no :hive-ansatz/env injected)"}

      (= "prove-lift" command)
      (let [lift (read-edn :lift)
            obligation (read-edn :obligation)
            store (or (tool-parameter parameters :store) path)]
        (if (and (map? lift) (map? obligation) (string? store))
          {:success true
           :report (certify/prove-lift! {:prover prover :env env :info info :codec codec :store store}
                                        lift obligation)}
          {:success false :error "lift, obligation and store parameters required"}))

      (nil? path)
      {:success false :error "path parameter required"}

      (= "export" command)
      {:success true :report (persistence/export! env info codec path {:exclude (or exclude #{})})}

      (= "import" command)
      {:success true :report (persistence/import! env info codec path)}

      (= "inspect" command)
      {:success true :report (persistence/inspect info codec path)}

      :else
      {:success false :error (str "unknown command: " command)})))

(defrecord HiveAnsatzAddon [state seed]
  addon/IAddon
  (addon-id [_] addon-id-value)
  (addon-type [_] :native)
  (capabilities [_] #{:tools :health-reporting :ansatz/proof-persistence})
  (initialize! [_ config]
    (let [merged (merge seed
                        (when (map? (:addon/config config)) (:addon/config config))
                        config)
          resolved (configured-ports merged)]
      (reset! state (select-keys resolved [:env :info :codec :prover]))
      (if (:error resolved)
        {:success? true
         :errors []
         :metadata {:degraded :recipes-only :reason (:error resolved)}}
        {:success? true :errors [] :metadata {}})))
  (shutdown! [_]
    (reset! state nil)
    nil)
  (tools [_]
    [{:name "ansatz_proofs"
      :description "Prover proof-persistence: export/import/inspect kernel decl snapshots (import re-verifies every theorem), prove-lift (define a lifted foreign function, prove its obligation, persist the proof as a content-addressed Fressian artifact), plus proving idioms (command=idioms)."
      :inputSchema {:type "object"
                    :properties {"command" {:type "string"
                                            :enum ["export" "import" "inspect" "prove-lift" "idioms"]
                                            :description "Operation"}
                                 "path" {:type "string"
                                         :description "Snapshot file path (export/import/inspect); store directory for prove-lift when :store is absent"}
                                 "exclude" {:type "array"
                                            :items {:type "string"}
                                            :description "[export] decl names to omit"}
                                 "lift" {:type "string"
                                         :description "[prove-lift] EDN of a Lift: {:name sym :forms [...]}"}
                                 "obligation" {:type "string"
                                               :description "[prove-lift] EDN of an Obligation: {:law-id kw :property {:theorem :params :prop :tactics}}"}
                                 "store" {:type "string"
                                          :description "[prove-lift] directory the content-addressed artifact is written under"}
                                 "tags" {:type "array"
                                         :items {:type "string"}
                                         :description "[idioms] filter tags"}}
                    :required ["command"]}
      :handler (fn [parameters] (handle-proofs-tool state parameters))}])
  (schema-extensions [_] [])
  (health [_]
    (let [{:keys [env]} @state]
      (if env
        {:status :ok :details {}}
        {:status :degraded :details {:reason :recipes-only}})))
  (excluded-tools [_] #{})
  (hooks [_] {}))

(defn init-as-addon!
  "Manifest entry point: build the addon record (uninitialized)."
  ([] (init-as-addon! {}))
  ([seed] (->HiveAnsatzAddon (atom nil) seed)))

(defn addon-ctor
  "Pure constructor - (config -> IAddon). Builds the uninitialized
   HiveAnsatzAddon record; performs NO registration and NO initialize! call.
   The mounter (hive-addon.mount) resolves this via :addon/init-fn and itself
   drives register!/initialize!. Additive: the self-registering `init-as-addon!`
   path remains for the current hive-mcp loader."
  [_config]
  (->HiveAnsatzAddon (atom nil) {}))