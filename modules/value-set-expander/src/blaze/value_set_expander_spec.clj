(ns blaze.value-set-expander-spec
  (:require
   [blaze.async.comp :as ac]
   [blaze.fhir.spec.spec]
   [blaze.spec]
   [blaze.value-set-expander :as vse]
   [blaze.value-set-expander.spec]
   [clojure.spec.alpha :as s]))

(s/fdef vse/expand-clauses
  :args (s/cat :expander :blaze/value-set-expander
               :type :fhir.resource/type
               :clauses :blaze.db.query/clauses)
  :ret ac/completable-future?)

(s/fdef vse/expand-condition-clauses
  :args (s/cat :expander :blaze/value-set-expander
               :type :fhir.resource/type
               :clauses (s/coll-of :blaze.db.query/search-clause :kind vector?))
  :ret ac/completable-future?)

(s/fdef vse/original-clauses
  :args (s/cat :expanded-clauses :blaze.db.query/clauses
               :clauses :blaze.db.query/clauses)
  :ret :blaze.db.query/clauses)

(s/fdef vse/expanded?
  :args (s/cat :clauses :blaze.db.query/clauses)
  :ret boolean?)
