(ns blaze.operation.graph.compiler.spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.operation.graph :as-alias graph]
   [blaze.operation.graph.compiled-graph :as-alias compiled-graph]
   [blaze.operation.graph.compiled-graph.link :as-alias link]
   [blaze.operation.graph.compiled-graph.node :as-alias node]
   [clojure.spec.alpha :as s]))

(s/def ::node/id
  string?)

(s/def ::compiled-graph/node
  (s/keys :req-un [::node/id :fhir.resource/type]))

(s/def ::link/source-id
  ::node/id)

(s/def ::link/target-id
  ::node/id)

(s/def ::link/resource-handles
  fn?)

(s/def ::compiled-graph/link
  (s/keys :req-un [::link/source-id ::link/target-id ::link/resource-handles]))

(s/def ::compiled-graph/start-node-id
  ::node/id)

(s/def ::compiled-graph/nodes
  (s/map-of ::node/id ::compiled-graph/node))

(s/def ::compiled-graph/links
  (s/map-of ::link/source-id ::compiled-graph/link))

(s/def ::graph/compiled-graph
  (s/keys
   :req-un
   [::compiled-graph/start-node-id
    ::compiled-graph/nodes
    ::compiled-graph/links]))
