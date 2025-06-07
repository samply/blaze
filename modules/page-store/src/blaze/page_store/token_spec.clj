(ns blaze.page-store.token-spec
  (:require
   [blaze.page-store.spec]
   [blaze.page-store.token :as token]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/fdef token/generate
  :args (s/cat :clauses :blaze.db.query/clauses)
  :ret :blaze.page-store/token)
