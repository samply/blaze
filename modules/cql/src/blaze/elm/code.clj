(ns blaze.elm.code
  "Implementation of the code type."
  (:require
    [blaze.elm.concept :as concept]
    [blaze.elm.protocols :as p]))


(defrecord Code [system version code]
  p/Equal
  (equal [this other]
    (some->> other (= this)))

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


;; 22.21. ToConcept
(extend-protocol p/ToConcept
  Code
  (to-concept [x]
    (concept/to-concept [x])))
