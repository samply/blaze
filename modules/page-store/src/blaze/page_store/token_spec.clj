(ns blaze.page-store.token-spec
  (:require
   [blaze.page-store.token :as token]
   [blaze.spec]
   [clojure.spec.alpha :as s])
  (:import
   [com.google.common.hash HashCode]))

(s/fdef token/encode
  :args (s/cat :hash #(instance? HashCode %))
  :ret :blaze.page-store/token)

(s/fdef token/generate
  :args (s/cat :clauses :blaze.db.query/clauses)
  :ret :blaze.page-store/token)
