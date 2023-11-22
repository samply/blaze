(ns blaze.page-store.cassandra.codec-spec
  (:require
   [blaze.anomaly-spec]
   [blaze.page-store :as page-store]
   [blaze.page-store.cassandra.codec :as codec]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef codec/decode
  :args (s/cat :bytes bytes? :token ::page-store/token)
  :ret (s/or :clauses :blaze.db.query/clauses :anomaly ::anom/anomaly))

(s/fdef codec/encode
  :args (s/cat :clauses :blaze.db.query/clauses)
  :ret bytes?)
