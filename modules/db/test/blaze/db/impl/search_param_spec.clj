(ns blaze.db.impl.search-param-spec
  (:require
   [blaze.byte-string-spec]
   [blaze.coll.core-spec]
   [blaze.coll.spec :as cs]
   [blaze.db.impl.batch-db.spec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.index :as-alias index]
   [blaze.db.impl.index.compartment.search-param-value-resource-spec]
   [blaze.db.impl.index.resource-search-param-value-spec]
   [blaze.db.impl.index.search-param-value-resource-spec]
   [blaze.db.impl.iterators-spec]
   [blaze.db.impl.search-param :as search-param]
   [blaze.db.impl.search-param.chained-spec]
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

(s/fdef search-param/index-handles
  :args (s/cat :search-param :blaze.db/search-param
               :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :compiled-values (s/coll-of some? :min-count 1)
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of ::index/handle))

(s/fdef search-param/estimated-scan-size
  :args (s/cat :search-param :blaze.db/search-param
               :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :compiled-values (s/coll-of some? :min-count 1))
  :ret (s/or :estimated-scan-size nat-int? :anomaly ::anom/anomaly))

(s/fdef search-param/sorted-index-handles
  :args (s/cat :search-param :blaze.db/search-param
               :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :direction :blaze.db.query/sort-direction
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of ::index/handle))

(s/fdef search-param/ordered-index-handles
  :args (s/cat :search-param :blaze.db/search-param-with-ordered-index-handles
               :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :compiled-values (s/coll-of some? :min-count 1)
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of ::index/handle))

(s/fdef search-param/ordered-compartment-index-handles
  :args (s/cat :search-param :blaze.db/search-param
               :batch-db :blaze.db.impl/batch-db
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid
               :compiled-values (s/coll-of some? :min-count 1)
               :start-id (s/? :blaze.db/id-byte-string))
  :ret (cs/coll-of ::index/handle))

(s/fdef search-param/matcher
  :args (s/cat :search-param :blaze.db/search-param
               :batch-db :blaze.db.impl/batch-db
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :compiled-values (s/coll-of some? :min-count 1))
  :ret fn?)

(s/fdef search-param/single-version-id-matcher
  :args (s/cat :search-param :blaze.db/search-param
               :batch-db :blaze.db.impl/batch-db
               :tid :blaze.db/tid
               :modifier (s/nilable :blaze.db.search-param/modifier)
               :compiled-values (s/coll-of some? :min-count 1))
  :ret fn?)

(s/fdef search-param/compartment-ids
  :args (s/cat :search-param :blaze.db/search-param
               :resource :fhir/Resource)
  :ret (s/or :ids (s/coll-of :blaze.resource/id) :anomaly ::anom/anomaly))

(s/fdef search-param/index-entries
  :args (s/cat :search-param :blaze.db/search-param
               :linked-compartments (s/nilable (s/coll-of (s/tuple string? string?)))
               :hash :blaze.resource/hash
               :resource :fhir/Resource)
  :ret (s/or :entries (cs/coll-of :blaze.db.kv/put-entry)
             :anomaly ::anom/anomaly))
