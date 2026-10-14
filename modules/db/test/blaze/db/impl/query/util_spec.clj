(ns blaze.db.impl.query.util-spec
  (:require
   [blaze.db.impl.index-spec]
   [blaze.db.impl.query.util :as qu]
   [blaze.db.index.query :as-alias query]
   [clojure.spec.alpha :as s]))

(s/fdef qu/match-extension-fn
  :args (s/cat :search-clauses (s/nilable ::query/search-clauses))
  :ret (s/nilable fn?))
