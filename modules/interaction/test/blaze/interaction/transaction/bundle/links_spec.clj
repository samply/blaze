(ns blaze.interaction.transaction.bundle.links-spec
  (:require
   [blaze.interaction.transaction.bundle.links :as links]
   [clojure.spec.alpha :as s]))

(s/fdef links/resolve-entry-links
  :args (s/cat :entries (s/coll-of map?)))
