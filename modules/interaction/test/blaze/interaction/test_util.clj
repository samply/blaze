(ns blaze.interaction.test-util
  (:require
   [blaze.async.comp :as ac]
   [blaze.handler.util :as handler-util]))

(def v3-ObservationValue
  "http://terminology.hl7.org/CodeSystem/v3-ObservationValue")

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defn coding
  ([system]
   (fn [codings]
     (filterv #(= system (:value (:system %))) codings)))
  ([codings system]
   (filterv #(= system (:value (:system %))) codings)))
