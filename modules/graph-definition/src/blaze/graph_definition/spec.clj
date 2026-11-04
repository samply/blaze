(ns blaze.graph-definition.spec
  (:require
   [blaze.fhir-path-spec]
   [blaze.fhir.spec.spec]
   [blaze.graph-definition :as-alias gd]
   [blaze.graph-definition.compiled-graph :as-alias compiled-graph]
   [blaze.graph-definition.compiled-graph.link :as-alias link]
   [blaze.graph-definition.compiled-graph.node :as-alias node]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def ::node/id
  string?)

(s/def ::compiled-graph/node
  (s/keys :req-un [::node/id :fhir.resource/type]))

(s/def ::link/source-id
  ::node/id)

(s/def ::link/target-id
  ::node/id)

(s/def ::link/path
  :blaze.fhir-path/expression)

(s/def ::link/clauses
  :blaze.db.query/search-clauses)

(s/def ::compiled-graph/link
  (s/keys :req-un [::link/source-id ::link/target-id]
          :opt-un [::link/path ::link/clauses]))

(s/def ::compiled-graph/start-node-id
  ::node/id)

(s/def ::compiled-graph/nodes
  (s/map-of ::node/id ::compiled-graph/node))

(s/def ::compiled-graph/links
  (s/map-of ::link/source-id (s/coll-of ::compiled-graph/link :kind vector?)))

(s/def ::gd/compiled-graph
  (s/keys
   :req-un
   [::compiled-graph/start-node-id
    ::compiled-graph/nodes
    ::compiled-graph/links]))
