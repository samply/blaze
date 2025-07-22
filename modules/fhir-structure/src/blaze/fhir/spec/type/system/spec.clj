(ns blaze.fhir.spec.type.system.spec
  (:require
   [blaze.fhir.spec.type.system :as system]
   [clojure.spec.alpha :as s]
   [clojure.spec.gen.alpha :as sg]))

(s/def :system/date
  (s/with-gen
    system/date?
    #(sg/fmap
      (partial apply system/date)
      (sg/tuple
       (s/gen (s/int-in 1 10000))
       (s/gen (s/int-in 1 13))
       (s/gen (s/int-in 1 29))))))

(s/def :system/date-time
  (s/with-gen
    system/date-time?
    #(sg/fmap
      (partial apply system/date-time)
      (sg/tuple
       (s/gen (s/int-in 1 10000))
       (s/gen (s/int-in 1 13))
       (s/gen (s/int-in 1 29))
       (s/gen (s/int-in 0 24))
       (s/gen (s/int-in 0 60))
       (s/gen (s/int-in 0 60))))))

(s/def :system/date-or-date-time
  (s/or :date :system/date :date-time :system/date-time))
