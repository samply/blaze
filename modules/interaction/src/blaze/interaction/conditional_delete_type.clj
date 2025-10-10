(ns blaze.interaction.conditional-delete-type
  "FHIR conditional delete interaction at type level.

  https://www.hl7.org/fhir/http.html#cdelete"
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util]
   [blaze.module :as m]
   [blaze.util :refer [str]]
   [blaze.util.clauses :as uc]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [integrant.core :as ig]
   [reitit.core :as reitit]
   [ring.util.response :as ring]
   [taoensso.timbre :as log]))

(defn- clauses->query-string [clauses]
  (->> clauses
       (map (fn [[param & values]] (str param "=" (str/join "," values))))
       (str/join "&")))

(defn- diagnostics [db type clauses]
  (let [num-resources (count (coll/eduction (filter (comp #{:delete} :op)) (d/changes db)))]
    (if (zero? num-resources)
      (if (seq clauses)
        (format "Success. No %ss were matched by query `%s`. Nothing to delete."
                type (clauses->query-string clauses))
        (format "Success. No %ss exist. Nothing to delete." type))
      (format "Successfully deleted %d %s%s."
              num-resources type (if (< 1 num-resources) "s" "")))))

(defn- build-response [db type return-preference clauses]
  (if (= :blaze.preference.return/OperationOutcome return-preference)
    (ring/response
     {:fhir/type :fhir/OperationOutcome
      :issue
      [{:fhir/type :fhir.OperationOutcome/issue
        :severity #fhir/code "success"
        :code #fhir/code "success"
        :diagnostics (type/string (diagnostics db type clauses))}]})
    (ring/status 204)))

(defmethod m/pre-init-spec :blaze.interaction/conditional-delete-type [_]
  (s/keys :req-un [:blaze.db/node]))

(defn- conditional-delete-op [type clauses]
  (cond-> [:conditional-delete type] (seq clauses) (conj clauses)))

(defmethod ig/init-key :blaze.interaction/conditional-delete-type [_ {:keys [node]}]
  (log/info "Init FHIR conditional delete type interaction handler")
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [headers query-params]}]
    (let [clauses (uc/search-clauses query-params)]
      (do-sync [db (d/transact node [(conditional-delete-op type clauses)])]
        (build-response db type (handler-util/preference headers "return")
                        clauses)))))
