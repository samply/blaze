(ns blaze.db.impl.search-param-spec
  (:require
    [blaze.byte-string-spec]
    [blaze.db.impl.batch-db.spec]
    [blaze.db.impl.codec-spec]
    [blaze.db.impl.index.compartment.search-param-value-resource-spec]
    [blaze.db.impl.index.resource-search-param-value-spec]
    [blaze.db.impl.index.search-param-value-resource-spec]
    [blaze.db.impl.iterators-spec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.impl.search-param.composite-spec]
    [blaze.db.impl.search-param.date-spec]
    [blaze.db.impl.search-param.has-spec]
    [blaze.db.impl.search-param.list-spec]
    [blaze.db.impl.search-param.quantity-spec]
    [blaze.db.impl.search-param.spec]
    [blaze.db.impl.search-param.string-spec]
    [blaze.db.impl.search-param.token-spec]
    [blaze.db.impl.search-param.util-spec]
    [blaze.db.kv-spec]
    [blaze.db.search-param-registry-spec]
    [blaze.fhir-path-spec]
    [blaze.fhir.spec]
    [blaze.spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/fdef search-param/compile-values
  :args (s/cat :search-param :blaze.db/search-param
               :modifier (s/nilable string?)
               :values (s/coll-of some? :min-count 1))
  :ret (s/or :compiled-values (s/coll-of some? :min-count 1)
             :anomaly ::anom/anomaly))


(s/fdef search-param/resource-handles
  :args (s/cat :search-param :blaze.db/search-param
               :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :values (s/coll-of some? :min-count 1)
               :start-did (s/? :blaze.db/did))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef search-param/sorted-resource-handles
  :args (s/cat :search-param :blaze.db/search-param
               :context :blaze.db.impl.batch-db/context
               :tid :blaze.db/tid
               :direction :blaze.db.query/sort-direction
               :start-did (s/? :blaze.db/did))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef search-param/compartment-resource-handles
  :args (s/cat :search-param :blaze.db/search-param
               :context :blaze.db.impl.batch-db/context
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :compiled-values (s/coll-of some? :min-count 1))
  :ret (s/coll-of :blaze.db/resource-handle :kind sequential?))


(s/fdef search-param/matches?
  :args (s/cat :search-param :blaze.db/search-param
               :context :blaze.db.impl.batch-db/context
               :resource-handle :blaze.db/resource-handle
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :compiled-values (s/coll-of some? :min-count 1))
  :ret boolean?)


(s/fdef search-param/compartment-ids
  :args (s/cat :search-param :blaze.db/search-param
               :resource :blaze/resource)
  :ret (s/or :ids (s/coll-of :blaze.resource/id) :anomaly ::anom/anomaly))


(s/fdef search-param/index-entries
  :args (s/cat :search-param :blaze.db/search-param
               :resource-id fn?
               :linked-compartments (s/nilable (s/coll-of :blaze.db/compartment))
               :did :blaze.db/did
               :hash :blaze.resource/hash
               :resource :blaze/resource)
  :ret (s/or :entries (s/coll-of :blaze.db.kv/put-entry-w-cf :kind sequential?)
             :anomaly ::anom/anomaly))
