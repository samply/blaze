(ns blaze.db.impl.db-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.byte-string-spec]
   [blaze.db.api-spec]
   [blaze.db.impl.codec-spec]
   [blaze.db.impl.codec.spec]
   [blaze.db.impl.db :as db]
   [blaze.db.impl.db.patient-everything-spec]
   [blaze.db.impl.index-spec]
   [blaze.db.impl.index.compartment.resource-spec]
   [blaze.db.impl.index.patient-last-change-spec]
   [blaze.db.impl.index.resource-as-of-spec]
   [blaze.db.impl.index.system-as-of-spec]
   [blaze.db.impl.index.system-stats-spec]
   [blaze.db.impl.index.type-as-of-spec]
   [blaze.db.impl.index.type-stats-spec]
   [blaze.db.impl.query.compartment-spec]
   [blaze.db.impl.query.system-spec]
   [blaze.db.impl.query.type-spec]
   [blaze.db.impl.query.util-spec]
   [blaze.db.impl.search-param-spec]
   [blaze.db.impl.search-param.chained-spec]
   [blaze.db.index.query :as-alias query]
   [blaze.db.kv-spec]
   [blaze.db.kv.spec]
   [clojure.spec.alpha :as s]))

(s/fdef db/->Matcher
  :args (s/cat :search-clauses ::query/search-clauses))

(s/fdef db/call-with-db
  :args (s/cat :node :blaze.db/node :t :blaze.db/t :since-t :blaze.db/t
               :f ifn?)
  :ret any?)

(s/fdef db/with-db
  :args (s/cat :node :blaze.db/node :t :blaze.db/t :f ifn?)
  :ret ac/completable-future?)
