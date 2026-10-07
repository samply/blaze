(ns blaze.page-store.cache-spec
  (:require
   [blaze.db.spec]
   [blaze.metrics.spec]
   [blaze.page-store.cache :as cache]
   [blaze.page-store.cache.spec]
   [blaze.page-store.hash-spec]
   [blaze.page-store.spec]
   [clojure.spec.alpha :as s]
   [java-time.api :as time]))

(s/fdef cache/create
  :args (s/cat :expire-duration time/duration?)
  :ret :blaze.page-store/cache)

(s/fdef cache/get
  :args (s/cat :clause-cache :blaze.page-store/cache
               :token-cache :blaze.page-store/cache
               :token :blaze.page-store/token)
  :ret (s/nilable :blaze.db.query/clauses))

(s/fdef cache/put!
  :args (s/cat :clause-cache :blaze.page-store/cache
               :token-cache :blaze.page-store/cache
               :clauses :blaze.db.query/non-empty-clauses)
  :ret :blaze.page-store/token)

(s/fdef cache/put-under-token!
  :args (s/cat :clause-cache :blaze.page-store/cache
               :token-cache :blaze.page-store/cache
               :token :blaze.page-store/token
               :clauses :blaze.db.query/non-empty-clauses)
  :ret nil?)

(s/fdef cache/collector
  :args (s/cat :clause-cache :blaze.page-store/cache
               :token-cache :blaze.page-store/cache)
  :ret :blaze.metrics/collector)
