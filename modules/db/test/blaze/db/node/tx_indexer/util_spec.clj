(ns blaze.db.node.tx-indexer.util-spec
  (:require
   [blaze.db.node.tx-indexer.util :as tx-u]
   [blaze.db.node.tx-indexer.util.spec]
   [blaze.db.search-param-registry.spec]
   [blaze.fhir.spec.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef tx-u/clauses->query-params
  :args (s/cat :clauses :blaze.db.query/search-clauses)
  :ret string?)

(s/fdef tx-u/rev-include-param
  :args (s/cat :type :fhir.resource/type :code string?)
  :ret ::tx-u/rev-include-param)

(s/fdef tx-u/conditional-create-msg
  :args (s/cat :type :fhir.resource/type :clauses :blaze.db.query/search-clauses)
  :ret string?)

(s/fdef tx-u/conditional-delete-msg
  :args (s/cat :type :fhir.resource/type :clauses :blaze.db.query/search-clauses)
  :ret string?)

(s/fdef tx-u/value-set-url
  :args (s/cat :search-param-registry :blaze.db/search-param-registry
               :type :fhir.resource/type
               :clauses :blaze.db.query/search-clauses)
  :ret (s/nilable string?))

(s/fdef tx-u/unexpanded-value-set-anom
  :args (s/cat :op-msg string? :url string?)
  :ret ::anom/anomaly)
