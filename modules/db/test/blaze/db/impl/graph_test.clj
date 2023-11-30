(ns blaze.db.impl.graph-test
  (:require
   [blaze.db.impl.graph :as graph]
   [clojure.test :as test :refer [are deftest]]))

(deftest get-reachables
  (let [test-graph {:1 [:2 :3] :2 [:1] :3 [:4 :3] :4 []}
        get-neighbors (fn [node] (test-graph node))
        get-reachable-from (graph/get-reachables get-neighbors)]
    (are [nodes reachables] (= (get-reachable-from nodes) reachables)
      [] #{}
      [:1] #{:1 :2 :3 :4}
      [:2] #{:1 :2 :3 :4}
      [:3] #{:3 :4}
      [:4] #{:4}
      [:1 :2] #{:1 :2 :3 :4}
      [:3 :4] #{:3 :4})))
