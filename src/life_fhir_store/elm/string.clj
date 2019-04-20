(ns life-fhir-store.elm.string
  "Implementation of the string type."
  (:require
    [life-fhir-store.elm.protocols :as p]))


;; 17.6. Indexer
(extend-protocol p/Indexer
  String
  (indexer [string index]
    (when (and index (<= 0 index) (< index (count string)))
      (.substring string index (inc index)))))
