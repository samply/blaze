(ns blaze.db.impl.search-param.core
  (:require
   [cognitect.anomalies :as anom]
   [taoensso.timbre :as log]))

(defmulti search-param
  "Converts a FHIR search parameter definition into a search-param.

  This multi-method is used to convert search parameters before storing them
  in the registry. Other namespaces can provide their own implementations here.

  The conversion can return an anomaly."
  {:arglists '([index definition])}
  (fn [_ {:keys [type]}] type))

(defmethod search-param :default
  [_ {:keys [url type]}]
  (log/warn (format "Skip creating search parameter `%s` of type `%s` because it is not implemented." url type))
  {::anom/category ::anom/unsupported})
