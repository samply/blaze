(ns blaze.interaction.history.util.spec
  (:require
   [blaze.db.spec]
   [blaze.fhir.spec.spec]
   [blaze.interaction.history.util :as-alias util]
   [blaze.spec]
   [clojure.spec.alpha :as s]))

(s/def ::page-size
  nat-int?)

(s/def ::since
  (s/nilable inst?))

(s/def ::summary
  #{:complete :summary})

(s/def ::page-t
  (s/nilable :blaze.db/t))

(s/def ::page-type
  (s/nilable :fhir.resource/type))

(s/def ::page-id
  (s/nilable :blaze.resource/id))

(s/def ::util/params
  (s/keys :req-un [::page-size ::since ::summary ::page-t ::page-type ::page-id]))
