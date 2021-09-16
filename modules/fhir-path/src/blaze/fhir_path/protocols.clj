(ns blaze.fhir-path.protocols)


(defprotocol Resolver
  (-resolve [_ uri] "Resolves `uri` into a resource."))
