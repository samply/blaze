(ns blaze.terminology-service
  (:require
   [blaze.terminology-service.protocols :as p]))

(defn expand-value-set
  "Returns a CompletableFuture that will complete with the expanded variant of
  the ValueSet specified in `request` or will complete exceptionally with an
  anomaly in case of an error."
  [terminology-service request]
  (p/-expand-value-set terminology-service request))
