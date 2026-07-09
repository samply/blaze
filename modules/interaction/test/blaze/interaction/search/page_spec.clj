(ns blaze.interaction.search.page-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.spec]
   [blaze.interaction.search.page :as page]
   [blaze.page-store.spec]
   [blaze.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef page/match-pull-opts
  :args (s/cat :params map?)
  :ret map?)

(s/fdef page/include-pull-opts
  :args (s/cat :params map?)
  :ret map?)

(s/fdef page/build-page
  :args (s/cat :duration-histogram some? :batch-db :blaze.db/db :pull fn?
               :include-defs (s/nilable map?) :page-size nat-int?
               :handles some?)
  :ret (s/or :page map? :anomaly ::anom/anomaly))

(s/fdef page/pull-matches
  :args (s/cat :pull-timer some?
               :match-futures (s/coll-of ac/completable-future? :kind vector?))
  :ret ac/completable-future?)

(s/fdef page/pull-includes
  :args (s/cat :duration-histogram some? :db :blaze.db/db :includes coll?
               :opts map?)
  :ret ac/completable-future?)

(s/fdef page/gen-token-fn
  :args (s/cat :context map? :request map?)
  :ret fn?)

(s/fdef page/gen-token!
  :args (s/cat :context map? :clauses (s/nilable :blaze.db.query/clauses))
  :ret ac/completable-future?)

(s/fdef page/self-link
  :args (s/cat :context map? :clauses (s/nilable :blaze.db.query/clauses))
  :ret map?)

(s/fdef page/first-link
  :args (s/cat :context map? :token (s/nilable :blaze.page-store/token)
               :clauses (s/nilable :blaze.db.query/clauses))
  :ret map?)

(s/fdef page/next-link
  :args (s/cat :context map? :token (s/nilable :blaze.page-store/token)
               :clauses (s/nilable :blaze.db.query/clauses) :offset map?)
  :ret map?)

(s/fdef page/prev-link
  :args (s/cat :context map? :token (s/nilable :blaze.page-store/token)
               :clauses (s/nilable :blaze.db.query/clauses) :offset map?)
  :ret map?)

(s/fdef page/next-link-offset
  :args (s/cat :params map? :page-start string? :offset map?)
  :ret map?)

(s/fdef page/prev-link-offset
  :args (s/cat :params map? :decode-page-start fn?)
  :ret map?)

(s/fdef page/zero-bundle
  :args (s/cat :context map? :clauses (s/nilable :blaze.db.query/clauses))
  :ret map?)

(s/fdef page/normal-bundle
  :args (s/cat :context map? :token (s/nilable :blaze.page-store/token)
               :page-data map?)
  :ret map?)

(s/fdef page/summary-response
  :args (s/cat :context map? :total nat-int?
               :clauses (s/nilable :blaze.db.query/clauses))
  :ret map?)
