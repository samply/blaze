(ns blaze.operation.graph.compiler.spec
  (:require
   [blaze.graph-definition.compiled-graph :as-alias gd-compiled-graph]
   [blaze.graph-definition.compiled-graph.link :as-alias gd-link]
   [blaze.graph-definition.spec]
   [blaze.operation.graph :as-alias graph]
   [blaze.operation.graph.compiled-graph :as-alias compiled-graph]
   [blaze.operation.graph.compiled-graph.link :as-alias link]
   [clojure.spec.alpha :as s]))

(s/def ::link/resource-handles
  fn?)

(s/def ::compiled-graph/link
  (s/merge ::gd-compiled-graph/link
           (s/keys :req-un [::link/resource-handles])))

(s/def ::compiled-graph/links
  (s/map-of ::gd-link/source-id (s/coll-of ::compiled-graph/link :kind vector?)))

(s/def ::graph/compiled-graph
  (s/keys
   :req-un
   [::gd-compiled-graph/start-node-id
    ::gd-compiled-graph/nodes
    ::compiled-graph/links]))
