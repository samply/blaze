(ns blaze.fhir.spec.type.system.spec
  (:require
    [blaze.fhir.spec.type.system :as system]
    [clojure.spec.alpha :as s]
    [clojure.spec.gen.alpha :as gen]))


(s/def :system/date
  (s/with-gen
    system/date?
    #(gen/fmap
       (partial apply system/date)
       (gen/tuple
         (s/gen (s/int-in 1 10000))
         (s/gen (s/int-in 1 13))
         (s/gen (s/int-in 1 29))))))
