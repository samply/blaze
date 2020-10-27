(ns blaze.elm.code
  "Implementation of the code type."
  (:require
    [blaze.elm.protocols :as p]))


(defrecord Code [system version code]
  p/Equivalent
  (equivalent [_ other]
    (and (= system (:system other))
         (= code (:code other))))

  p/Children
  (children [_]
    [code nil system version])

  p/Descendents
  (descendents [_]
    [code nil system version]))


(defn to-code
  "Returns a CQL code with isn't the same as a FHIR code from the database."
  [system version code]
  (->Code system version code))
