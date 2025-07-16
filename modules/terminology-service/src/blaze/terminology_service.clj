(ns blaze.terminology-service
  (:require
   [blaze.terminology-service.protocols :as p]))

(defn code-systems
  "Returns a CompletableFuture that will complete with a list of
  TerminologyCapabilities codeSystem entries or will complete exceptionally with
  an anomaly in case of an error."
  [terminology-service]
  (p/-code-systems terminology-service))

(defn code-system-lookup
  [terminology-service params]
  (prn "b.t-s code-system-lookup")
  (p/-code-system-lookup terminology-service params))

(defn code-system-validate-code
  [terminology-service params]
  (prn "b.t-s code-system-validate-code")
  (p/-code-system-validate-code terminology-service params))

(defn expand-value-set
  "Returns a CompletableFuture that will complete with the expanded variant of
  the ValueSet specified in `params` or will complete exceptionally with an
  anomaly in case of an error."
  [terminology-service params]
  (p/-expand-value-set terminology-service params))

(defn value-set-validate-code
  [terminology-service params]
  (p/-value-set-validate-code terminology-service params))
