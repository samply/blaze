(ns blaze.terminology-service.local.code-system.sct.context-spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [blaze.path.spec]
   [blaze.terminology-service.local.code-system.sct.context :as context]
   [blaze.terminology-service.local.code-system.sct.spec]
   [clojure.spec.alpha :as s]
   [cognitect.anomalies :as anom]))

(s/fdef context/find-concept
  :args (s/cat :module-dependency-index map? :concept-index map?
               :module-id :sct/id :version :sct/time :concept-id :sct/id)
  :ret (s/nilable boolean?))

(s/fdef context/find-fully-specified-name
  :args (s/cat :module-dependency-index map? :description-index map?
               :module-id :sct/id :version :sct/time :concept-id :sct/id)
  :ret (s/nilable string?))

(s/fdef context/find-synonyms
  :args (s/cat :module-dependency-index map? :description-index map?
               :module-id :sct/id :version :sct/time :concept-id :sct/id)
  :ret (s/coll-of (s/tuple string? string?)))

(s/fdef context/neighbors
  :args (s/cat :index map? :module-id :sct/id :version :sct/time
               :concept-id :sct/id)
  :ret (s/coll-of :sct/id :kind set?))

(s/fdef context/transitive-neighbors
  :args (s/cat :index map? :module-id :sct/id :version :sct/time
               :concept-id :sct/id)
  :ret (s/coll-of :sct/id :kind set?))

(s/fdef context/transitive-neighbors-including
  :args (s/cat :index map? :module-id :sct/id :version :sct/time
               :concept-id :sct/id)
  :ret (s/coll-of :sct/id :kind set?))

(s/fdef context/build
  :args (s/cat :path :blaze/dir)
  :ret (s/or :context :sct/context :anomaly ::anom/anomaly))
