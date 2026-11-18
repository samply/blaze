(ns blaze.interaction.conditional-delete-type
  "FHIR conditional delete interaction at type level.

  https://www.hl7.org/fhir/http.html#cdelete"
  (:refer-clojure :exclude [str])
  (:require
   [blaze.async.comp :as ac :refer [do-sync]]
   [blaze.coll.core :as coll]
   [blaze.db.api :as d]
   [blaze.db.spec]
   [blaze.fhir.search-clauses :as fsc]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.util :as handler-util]
   [blaze.module :as m]
   [blaze.util :refer [str]]
   [blaze.value-set-expander :as vse]
   [blaze.value-set-expander.spec]
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

(defn- num-deleted [db]
  (coll/count-reducible (coll/eduction (filter (comp #{:delete} :op)) (d/changes db))))

(defn- diagnostics [num-resources type clauses]
  (if (zero? num-resources)
    (if (seq clauses)
      (format "Success. No %ss were matched by query `%s`. Nothing to delete."
              type (clauses->query-string clauses))
      (format "Success. No %ss exist. Nothing to delete." type))
    (format "Successfully deleted %d %s%s."
            num-resources type (if (< 1 num-resources) "s" ""))))

(defn- outcome-response
  "Builds the OperationOutcome response of a conditional delete of resources
  matching the client's `clauses` with `db`, the database after the delete."
  [db type clauses]
  (ac/completed-future
   (ring/response
    {:fhir/type :fhir/OperationOutcome
     :issue
     [{:fhir/type :fhir.OperationOutcome/issue
       :severity #fhir/code "success"
       :code #fhir/code "success"
       :diagnostics (type/string (diagnostics (num-deleted db) type clauses))}]})))

(defn- conditional-delete-op [type clauses]
  (cond-> [:conditional-delete type] (seq clauses) (conj clauses)))

(defn- operation-outcome-preferred? [headers]
  (= :blaze.preference.return/OperationOutcome
     (handler-util/preference headers "return")))

(defn- expand-value-sets [value-set-expander type clauses]
  (if (seq clauses)
    (vse/expand-condition-clauses value-set-expander type clauses)
    (ac/completed-future clauses)))

(defn- conditional-delete
  "Deletes the resources of `type` matching `expanded-clauses`, the client's
  `clauses` with expanded value sets.

  Returns a CompletableFuture of the response. Only an OperationOutcome
  response, which states the number of deleted resources, needs the database
  after the delete."
  [node type headers clauses expanded-clauses]
  (let [tx-ops [(conditional-delete-op type expanded-clauses)]]
    (if (operation-outcome-preferred? headers)
      (d/transact-then node tx-ops #(outcome-response % type clauses))
      (do-sync [_ (d/transact node tx-ops)]
        (ring/status 204)))))

(defmethod m/pre-init-spec :blaze.interaction/conditional-delete-type [_]
  (s/keys :req-un [:blaze.db/node :blaze/value-set-expander]))

(defmethod ig/init-key :blaze.interaction/conditional-delete-type
  [_ {:keys [node value-set-expander]}]
  (log/info "Init FHIR conditional delete type interaction handler")
  (fn [{{{:fhir.resource/keys [type]} :data} ::reitit/match
        :keys [headers query-params]}]
    (let [clauses (fsc/search-clauses query-params)]
      (-> (expand-value-sets value-set-expander type clauses)
          (ac/then-compose
           (partial conditional-delete node type headers clauses))))))
