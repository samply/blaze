(ns blaze.validator
  (:require
   [blaze.validator.protocols :as p]))

(defn validate
  "Validates `resource` using `validator`.

  Returns a CompletableFuture that will complete with the resource to persist
  (possibly tagged as invalid) or will complete exceptionally with an anomaly in
  case the resource is rejected or the validator is unavailable."
  [validator resource]
  (p/-validate validator resource))
