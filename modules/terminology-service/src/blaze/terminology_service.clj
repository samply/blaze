(ns blaze.terminology-service
  (:require
   [blaze.terminology-service.protocols :as p]))

(defn post-init!
  "Post intitialization hat supplies the database node and ensuares that
  built-in code systems exist.

  The database node can't be supplied at normal initialization, because the
  terminology service is needed by the node itself.

  This function will block and return an anomaly on erros."
  [terminology-service node]
  (p/-post-init terminology-service node))

(defn code-systems
  "Returns a CompletableFuture that will complete with a list of
  TerminologyCapabilities codeSystem entries or will complete exceptionally with
  an anomaly in case of an error."
  [terminology-service]
  (p/-code-systems terminology-service))

(defn code-system-validate-code
  [terminology-service params]
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
