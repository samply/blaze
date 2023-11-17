(ns blaze.interaction.search.include-spec
  (:require
   [blaze.coll.spec :as cs]
   [blaze.db.spec]
   [blaze.interaction.search.include :as include]
   [blaze.interaction.search.spec]
   [clojure.spec.alpha :as s]))

(s/fdef include/add-includes
  :args (s/cat :db :blaze.db/db
               :include-defs :blaze.interaction.search/include-defs
               :resource-handles (cs/coll-of :blaze.db/resource-handle)))
