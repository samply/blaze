(ns blaze.operation.graph.cache-test
  (:require
   [blaze.fhir.search-clauses-spec]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.test-util]
   [blaze.fhir.test-util.graph-definition :as g-tu]
   [blaze.fhir.test-util.graph-definition-spec]
   [blaze.graph-definition-spec]
   [blaze.operation.graph :as graph]
   [blaze.operation.graph.cache :as cache]
   [blaze.operation.graph.cache-spec]
   [blaze.operation.graph.compiler-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [juxt.iota :refer [given]])
  (:import
   [com.github.benmanes.caffeine.cache Cache]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- new-cache
  ([]
   (ig/init-key ::graph/compiled-graph-cache {}))
  ([num-nodes]
   (ig/init-key ::graph/compiled-graph-cache {:num-nodes num-nodes})))

(defn- cache-size
  "Returns the number of entries of `cache` after evicting entries exceeding
  its maximum weight."
  [cache]
  (.cleanUp ^Cache cache)
  (count (.asMap ^Cache cache)))

(defn- graph-def
  "Creates a GraphDefinition with a single `patient` node and `version-id`,
  which is part of the cache key."
  [version-id & {:keys [start] :or {start "patient"}}]
  (assoc (g-tu/graph-def :start start :nodes [(g-tu/node "patient" "Patient")])
         :meta (type/meta {:versionId (type/id version-id)})))

(deftest compile-test
  (testing "valid graph definition"
    (let [cache (new-cache)
          graph (cache/compile cache (graph-def "1"))]

      (testing "is compiled"
        (given graph
          :start-node-id := "patient"
          :nodes := {"patient" {:id "patient" :type "Patient"}}
          :links := {}))

      (testing "is cached"
        (is (= 1 (cache-size cache)))
        (is (identical? graph (cache/compile cache (graph-def "1")))))

      (testing "another version is cached separately"
        (is (not (identical? graph (cache/compile cache (graph-def "2")))))
        (is (= 2 (cache-size cache))))))

  (testing "invalid graph definition"
    (let [cache (new-cache)]

      (testing "returns an anomaly"
        (given (cache/compile cache (graph-def "1" :start "foo"))
          ::anom/category := ::anom/incorrect
          ::anom/message := "Invalid start node id `foo` not referring to a node."))

      (testing "is cached"
        (let [anomaly (cache/compile cache (graph-def "1" :start "foo"))]
          (is (= 1 (cache-size cache)))
          (is (identical? anomaly (cache/compile cache (graph-def "1" :start "foo"))))))))

  (testing "invalid graph definitions are evicted"
    (let [cache (new-cache 1)]
      (doseq [version-id ["1" "2" "3"]]
        (cache/compile cache (graph-def version-id :start "foo")))

      (is (= 1 (cache-size cache))))))
