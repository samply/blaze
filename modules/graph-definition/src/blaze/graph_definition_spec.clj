(ns blaze.graph-definition-spec
  (:require
   [blaze.fhir.search-clauses-spec]
   [blaze.fhir.spec.spec]
   [blaze.graph-definition :as gd]
   [blaze.graph-definition.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef gd/compile
  :args (s/cat :graph-def :fhir/GraphDefinition)
  :ret (s/or :compiled-graph ::gd/compiled-graph :anomaly ::anom/anomaly))

(s/fdef gd/ref-clause?
  :args (s/cat :clause (s/spec :blaze.db.query/search-clause))
  :ret boolean?)

(s/fdef gd/rev-links
  :args (s/cat :compiled-graph ::gd/compiled-graph)
  :ret (s/or :rev-links (s/coll-of ::gd/rev-link :kind vector?)
             :anomaly ::anom/anomaly))
