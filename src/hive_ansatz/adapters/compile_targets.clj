(ns hive-ansatz.adapters.compile-targets
  "Concrete ICompileTarget adapters for :jvm, :cljw and :cljrs.

   BOUNDARY: writes the unit, spawns the compiler, executes the artifact. Every
   lowering decision is read from `compile.target/target-profiles`, so the
   native side is ONE record keyed by data — a further native target is a
   profile entry, not another impl.

   A native arm answers from its compiled ARTIFACT, never from a dialect nREPL."
  (:require [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [clojure.string :as str]
            [malli.core :as m]
            [hive-ansatz.compile.driver :as driver]
            [hive-ansatz.compile.oracle :as oracle]
            [hive-ansatz.compile.target :as tgt]
            [hive-ansatz.schema :as schema]))

(def ^:private stderr-tail-lines
  "How many trailing stderr lines a refusal carries back."
  4)

(def ^:private no-such-probe
  "Error a target reports when asked for a probe it was not built with."
  "probe not baked into this artifact")

(defn- expand-home [path]
  (if (str/starts-with? path "~/")
    (str (System/getProperty "user.home") (subs path 1))
    path))

(defn- compiler-bin [{:keys [bin target]}]
  (when-not bin
    (throw (ex-info "compile target declares no :bin" {:target target})))
  (expand-home (or (System/getenv (:env bin)) (:default bin))))

(defn- work-dir! [target]
  (doto (io/file (System/getProperty "java.io.tmpdir")
                 (str "hive-ansatz-" (name target) "-" (System/nanoTime)))
    (.mkdirs)))

(defn- tail [s]
  (->> (str/split-lines (str s))
       (remove str/blank?)
       (take-last stderr-tail-lines)
       (str/join "\n")))

(defn- build!
  "Emit `kernel`'s unit and run `profile`'s compiler over it, returning a
   KernelArtifact. Throws ex-info carrying the compiler's own diagnosis when
   the build fails or a gate refuses it."
  [profile kernel]
  (let [dir  (work-dir! (:target profile))
        unit (io/file dir (driver/unit-file-name profile kernel))
        out  (io/file dir (str "kernel-" (name (:target profile))))]
    (io/make-parents unit)
    (spit unit (driver/unit-source profile kernel))
    (let [argv (driver/build-argv profile {:kernel/ns    (:ns kernel)
                                           :kernel/file  (.getPath unit)
                                           :artifact/out (.getPath out)})
          {:keys [exit err]} (apply shell/sh (concat [(compiler-bin profile)]
                                                     argv
                                                     [:dir (.getPath dir)]))]
      (when-not (and (zero? exit) (.exists out))
        (throw (ex-info "compile target refused the kernel"
                        {:target (:target profile)
                         :exit   exit
                         :gates  (:gates profile)
                         :stderr (tail err)})))
      {:target (:target profile) :handle (.getPath out) :probes (:probes kernel)})))

(defn- exec-probe
  "Execute `artifact` for the probe at `index`; answer a CheckResult."
  [target artifact probe index]
  (let [{:keys [exit out err]} (shell/sh (:handle artifact) (str index))
        value (str/trim (str out))]
    (if (and (zero? exit) (seq value))
      (oracle/->check target probe true value)
      (oracle/->check target probe false
                      (if (zero? exit) "artifact printed nothing" (tail err))))))

(defrecord JvmTarget []
  tgt/ICompileTarget
  (target-key [_] :jvm)
  (compile-kernel [_ kernel]
    (load-string (str "(ns " (:ns kernel) ")\n" (:source kernel)))
    {:target :jvm :handle (:ns kernel) :probes (:probes kernel)})
  (run-probe [_ artifact probe]
    (if (driver/probe-index artifact probe)
      (try
        (oracle/->check :jvm probe true
                        (pr-str (binding [*ns* (find-ns (symbol (:handle artifact)))]
                                  (eval (read-string probe)))))
        (catch Throwable t
          (oracle/->check :jvm probe false (ex-message t))))
      (oracle/->check :jvm probe false no-such-probe))))

(defrecord NativeTarget [target]
  tgt/ICompileTarget
  (target-key [_] target)
  (compile-kernel [_ kernel] (build! (tgt/profile target) kernel))
  (run-probe [_ artifact probe]
    (if-let [i (driver/probe-index artifact probe)]
      (exec-probe target artifact probe i)
      (oracle/->check target probe false no-such-probe))))

(defn jvm-target
  "The reference arm: the kernel evaluated in THIS JVM."
  []
  (->JvmTarget))

(defn cljw-target
  "The ClojureWasm arm: a self-contained native binary."
  []
  (->NativeTarget :cljw))

(defn cljrs-target
  "The clojurust arm: an AOT native artifact under its declared gates."
  []
  (->NativeTarget :cljrs))

(defn target-for
  "The adapter serving `k`, chosen by that target's declared :runner. Throws on
   a target whose profile declares no runner."
  [k]
  (case (:runner (tgt/profile k))
    :in-process (jvm-target)
    :binary     (->NativeTarget k)
    (throw (ex-info "compile target declares no :runner" {:target k}))))

(m/=> target-for [:=> [:cat schema/CompileTargetKey] :any])

(defn run-kernel
  "Compile `kernel` on each of `target-keys` and answer every probe on each,
   returning one OracleVerdict per probe.

   An arm whose build is refused contributes a failed CheckResult for every
   probe instead of aborting the run: a refusing gate is a verdict, not a
   crash."
  [target-keys kernel]
  (let [checks (vec (for [k target-keys
                          :let [t   (target-for k)
                                art (try (tgt/compile-kernel t kernel)
                                         (catch clojure.lang.ExceptionInfo e e))]
                          probe (:probes kernel)]
                      (if (instance? Throwable art)
                        (oracle/->check k probe false (ex-message art))
                        (tgt/run-probe t art probe))))]
    (mapv (fn [probe] (oracle/verdict probe (filterv #(= probe (:probe %)) checks)))
          (:probes kernel))))

(m/=> run-kernel
      [:=> [:cat [:sequential schema/CompileTargetKey] schema/Kernel]
       [:vector schema/OracleVerdict]])
