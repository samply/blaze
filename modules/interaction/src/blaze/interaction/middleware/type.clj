(ns blaze.interaction.middleware.type
  (:require
    [blaze.datomic.util :as util]
    [blaze.handler.util :as handler-util]
    [clojure.spec.alpha :as s]
    [cognitect.anomalies :as anom]
    [datomic.api :as d]
    [datomic-spec.core :as ds]))


(s/fdef wrap-type
  :args (s/cat :handler fn? :conn ::ds/conn))

(defn wrap-type
  [handler conn]
  (fn [{{:keys [type]} :path-params :as request}]
    (if (util/cached-entity (d/db conn) (keyword type))
      (handler request)
      (handler-util/error-response
        {::anom/category ::anom/not-found
         :fhir/issue "not-found"}))))
