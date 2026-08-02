(ns blaze.fhir.type-metadata-spec
  (:require
   [blaze.fhir.structure-definition-repo.spec]
   [blaze.fhir.type-metadata :as tm]
   [blaze.fhir.type-metadata.spec]
   [clojure.spec.alpha :as s]))

(s/fdef tm/create-type-metadata
  :args (s/cat :element-definitions (s/coll-of map?))
  :ret :blaze.fhir/type-metadata-registry)

(s/fdef tm/build
  :args (s/cat :structure-definition-repo :blaze.fhir/structure-definition-repo)
  :ret :blaze.fhir/type-metadata-registry)

(s/fdef tm/registry
  :args (s/cat)
  :ret :blaze.fhir/type-metadata-registry)

(s/fdef tm/type-metadata
  :args (s/cat :type keyword?))
