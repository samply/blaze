(ns blaze.fhir.operation.graphql.middleware
  (:require
   [blaze.fhir.operation.graphql.middleware.query :refer [wrap-query]]
   [integrant.core :as ig]))

(defmethod ig/init-key ::query [_ _]
  {:name ::query
   :wrap wrap-query})
