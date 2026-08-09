(ns hive-ansatz.compile.driver-test
  "Trifecta for the pure unit-lowering fns, plus hand-written laws for the shape
   constraints the native dialects impose on a generated driver."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest testing is]]
            [hive-schemas.test :as hst]
            [hive-ansatz.schema :as schema]
            [hive-ansatz.compile.driver :as driver]
            [hive-ansatz.compile.target :as tgt]))

(hst/deftrifecta-from-schema driver-source-conforms
  hive-ansatz.compile.driver/driver-source
  ;; :mutation false — schema-mutants derives only from required OUTPUT KEYS, so
  ;; a scalar :string return has none. The emitted-shape laws below discriminate.
  {:in  [:cat [:vector :string]]
   :out [:string {:min 1}]
   :rel (fn [[probes] out]
          (and (str/includes? out ":else")
               (str/includes? out "-main")
               (every? #(str/includes? out (str "(= i \"" % "\")"))
                       (range (count probes)))))
   :mutation false
   :num-tests 100})

(hst/deftrifecta-from-schema unit-file-name-conforms
  hive-ansatz.compile.driver/unit-file-name
  {:in  [:cat schema/CompileTarget schema/Kernel]
   :out [:string {:min 4}]
   :rel (fn [_ out] (str/ends-with? out ".clj"))
   :mutation false
   :num-tests 100})

(def ^:private kernel
  {:ns "hive.kernel.demo"
   :source "(defn add2 [n] (+ n 2))\n"
   :probes ["(add2 40)" "(add2 0)"]})

(deftest driver-dispatches-without-case
  (testing "cljrs codegen rejects `case`; the emitted driver must not use one"
    (let [src (driver/driver-source (:probes kernel))]
      (is (not (re-find #"\(case\s" src)))
      (is (str/includes? src "cond"))))
  (testing "argv is read from -main's rest arg, not *command-line-args*"
    (let [src (driver/driver-source (:probes kernel))]
      (is (not (str/includes? src "*command-line-args*")))
      (is (str/includes? src "[& args]"))))
  (testing "a kernel with no probes still emits a well-formed driver"
    (is (str/includes? (driver/driver-source []) ":else"))))

(deftest unit-source-follows-the-profile
  (testing "the ns form rides on the profile, not on the kernel"
    (is (str/includes? (driver/unit-source (tgt/profile :cljw) kernel)
                       "(ns hive.kernel.demo)"))
    (is (not (str/includes? (driver/unit-source (tgt/profile :cljrs) kernel)
                            "(ns "))))
  (testing "the kernel's own source always survives into the unit"
    (doseq [k [:jvm :cljw :cljrs]]
      (is (str/includes? (driver/unit-source (tgt/profile k) kernel)
                         (:source kernel))
          (str k)))))

(deftest unit-file-name-tracks-the-namespace
  (is (= "hive/kernel/demo.clj" (driver/unit-file-name (tgt/profile :cljw) kernel)))
  (is (= "unit.clj" (driver/unit-file-name (tgt/profile :cljrs) kernel)))
  (testing "munging follows the namespace, not the file"
    (is (= "hive/kernel_x/demo.clj"
           (driver/unit-file-name (tgt/profile :cljw)
                                  (assoc kernel :ns "hive.kernel-x.demo"))))))

(deftest gates-lower-to-compiler-flags
  (let [ctx {:kernel/ns "k" :kernel/file "k.clj" :artifact/out "/tmp/out"}]
    (testing "a declared gate appends its flag"
      (is (= ["compile" "-o" "/tmp/out" "k.clj" "--require-fully-compiled"]
             (driver/build-argv (tgt/profile :cljrs) ctx))))
    (testing "a target with no gates appends nothing"
      (is (= ["build" "-m" "k" "-o" "/tmp/out" "k.clj"]
             (driver/build-argv (tgt/profile :cljw) ctx))))
    (testing "dropping the gate drops the flag — the gate IS the flag"
      (is (= ["compile" "-o" "/tmp/out" "k.clj"]
             (driver/build-argv (assoc (tgt/profile :cljrs) :gates #{}) ctx))))))

(deftest build-argv-refuses-to-guess
  (testing "a target with no template is an error, not an empty argv"
    (is (thrown? clojure.lang.ExceptionInfo
                 (driver/build-argv (tgt/profile :jvm) {}))))
  (testing "an unbound placeholder is an error, not a nil in the argv"
    (is (thrown? clojure.lang.ExceptionInfo
                 (driver/build-argv (tgt/profile :cljrs) {:kernel/file "k.clj"})))))

(deftest probe-index-addresses-only-baked-probes
  (let [artifact {:target :cljw :handle "/tmp/bin" :probes (:probes kernel)}]
    (is (= 0 (driver/probe-index artifact "(add2 40)")))
    (is (= 1 (driver/probe-index artifact "(add2 0)")))
    (is (nil? (driver/probe-index artifact "(add2 99)")))))

(deftest shipped-targets-declare-how-they-run
  (testing "every profile the library ships is runnable, though OCP entries need not be"
    (doseq [k [:jvm :cljw :cljrs]]
      (is (#{:in-process :binary} (:runner (tgt/profile k))) (str k))))
  (testing "every :binary target declares a compiler and a template"
    (doseq [[k p] tgt/target-profiles
            :when (= :binary (:runner p))]
      (is (:bin p) (str k))
      (is (:build-args p) (str k)))))
