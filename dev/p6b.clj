(ns p6b
  "Scratchpad for NATIVE-KERNELS-P6b: shaping the ICompileTarget driver against
   live cljw/cljrs nREPLs before the forms are promoted into src."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]))

;; ---------------------------------------------------------------- fixtures

(def demo-kernel
  {:ns "hive.kernel.demo"
   :source (str "(defn add2 [n] (+ n 2))\n"
                "(defn sum-to [n] (if (< n 1) 0 (+ n (sum-to (- n 1)))))\n")
   :probes ["(add2 40)" "(sum-to 10)"]})

;; ---------------------------------------------------------------- pure

(defn driver-source
  "The generated -main that bakes PROBES in at compile time.
   cond+= on the raw argv string: cljrs codegen rejects `case`, and
   *command-line-args* is nil in a cljrs AOT binary."
  [probes]
  (let [clauses (->> probes
                     (map-indexed (fn [i p] (str "(= i \"" i "\") " p)))
                     (str/join "\n                       "))]
    (str "(defn -main [& args]\n"
         "  (let [i (first args)]\n"
         "    (println (pr-str (cond " clauses "\n"
         "                       :else :hive-ansatz/unknown-probe)))))\n")))

(defn kernel-file-source [{:keys [ns source probes]}]
  (str "(ns " ns ")\n\n" source "\n" (driver-source probes)))
