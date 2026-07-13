(ns hive-ansatz.recipes-test
  "Idioms-as-data contracts: every idiom conforms to the schema, ids are
   unique, and find-idioms filters by tag intersection (OCP surface)."
  (:require [clojure.test :refer [deftest testing is]]
            [malli.core :as m]
            [hive-ansatz.schema :as schema]
            [hive-ansatz.recipes :as recipes]))

(deftest idioms-conform
  (doseq [idiom recipes/idioms]
    (testing (str (:id idiom))
      (is (m/validate schema/Idiom idiom)
          (pr-str (m/explain schema/Idiom idiom))))))

(deftest idiom-ids-unique
  (is (= (count recipes/idioms)
         (count (set (map :id recipes/idioms))))))

(deftest find-idioms-contract
  (testing "nil/empty tags return all"
    (is (= recipes/idioms (recipes/find-idioms nil)))
    (is (= recipes/idioms (recipes/find-idioms #{}))))
  (testing "tag filter intersects"
    (let [rewrites (recipes/find-idioms #{:rewrite})]
      (is (seq rewrites))
      (is (every? #(contains? (:tags %) :rewrite) rewrites))))
  (testing "unknown tag yields nothing"
    (is (empty? (recipes/find-idioms #{:no-such-tag})))))
