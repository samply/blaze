(ns blaze.fhir.operation.evaluate-measure.test-util
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.elm.compiler.external-data :as ed]
   [blaze.elm.compiler.external-data-spec]
   [blaze.handler.util :as handler-util]))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defn- handle [subject-handle]
  {:population-handle subject-handle :subject-handle subject-handle})

(defn handle-mapper [db]
  (comp (ed/resource-mapper db) (map handle)))

(defn resource [db type id]
  (ed/mk-resource db (d/resource-handle db type id)))
