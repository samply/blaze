(ns blaze.terminology-service
  (:require
   [blaze.terminology-service.protocols :as p]))

(defn code-systems
  "Returns a CompletableFuture that will complete with a list of
  TerminologyCapabilities codeSystem entries or will complete exceptionally with
  an anomaly in case of an error."
  [terminology-service]
  (p/-code-systems terminology-service))

(defn code-system-validate-code
  [terminology-service request]
  (p/-code-system-validate-code terminology-service request))

(defn expand-value-set
  "Returns a CompletableFuture that will complete with the expanded variant of
  the ValueSet specified in `request` or will complete exceptionally with an
  anomaly in case of an error."
  [terminology-service request]
  (p/-expand-value-set terminology-service request))

(defn value-set-validate-code
  [terminology-service request]
  (p/-value-set-validate-code terminology-service request))
