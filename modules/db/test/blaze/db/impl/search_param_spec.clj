(ns blaze.db.impl.search-param-spec
  (:require
    [blaze.db.impl.codec-spec]
    [blaze.db.search-param-registry-spec]
    [blaze.db.impl.search-param :as search-param]
    [blaze.db.kv-spec]
    [blaze.fhir.spec]
    [blaze.fhir-path-spec]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]))


(s/def :blaze.db.search-param/iterator
  #(satisfies? search-param/Iterator %))


(s/fdef search-param/first
  :args (s/cat :iterator :blaze.db.search-param/iterator))


(s/fdef search-param/next
  :args (s/cat :iterator :blaze.db.search-param/iterator :current-id bytes?))


(s/def :blaze.db.compartment/c-hash
  :blaze.db/c-hash)


(s/def :blaze.db.compartment/res-id
  bytes?)


(s/def :blaze.db/compartment
  (s/keys :req-un [:blaze.db.compartment/c-hash :blaze.db.compartment/res-id]))


(s/fdef search-param/new-iterator
  :args (s/cat :search-param :blaze.db/search-param :snapshot :blaze.db/kv-snapshot
               :tid :blaze.db/tid :values (s/coll-of string? :min-count 1))
  :ret :blaze.db.search-param/iterator)


(s/fdef search-param/new-compartment-iterator
  :args (s/cat :search-param :blaze.db/search-param :cspvi :blaze.db/kv-iterator
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid :values (s/coll-of string? :min-count 1))
  :ret :blaze.db.search-param/iterator)


(s/fdef search-param/matches?
  :args (s/cat :search-param :blaze.db/search-param :snapshot :blaze.db/kv-snapshot
               :tid :blaze.db/tid :id bytes? :hash :blaze.resource/hash
               :values (s/coll-of string? :min-count 1))
  :ret boolean?)


(s/fdef search-param/compartment-matches?
  :args (s/cat :search-param :blaze.db/search-param :snapshot :blaze.db/kv-snapshot
               :compartment :blaze.db/compartment
               :tid :blaze.db/tid :id bytes? :hash :blaze.resource/hash
               :values (s/coll-of string? :min-count 1))
  :ret boolean?)


(s/fdef search-param/compartment-ids
  :args (s/cat :search-param :blaze.db/search-param
               :resource :blaze/resource)
  :ret (s/coll-of :blaze.resource/id))


(s/fdef search-param/index-entries
  :args (s/cat :search-param :blaze.db/search-param
               :hash :blaze.resource/hash
               :resource :blaze/resource
               :linked-compartments (s/coll-of :blaze.db/compartment))
  :ret (s/or :entries (s/coll-of :blaze.db.kv/put-entry-w-cf)
             :anomaly ::anom/anomaly))
