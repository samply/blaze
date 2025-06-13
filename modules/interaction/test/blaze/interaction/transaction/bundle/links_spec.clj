(ns blaze.interaction.transaction.bundle.links-spec
  (:require
   [blaze.fhir.spec.spec]
   [blaze.interaction.transaction.bundle.links :as links]
   [clojure.spec.alpha :as s]))

(s/fdef links/resolve-entry-links
  :args (s/cat :entries (s/coll-of :fhir.Bundle/entry)))
