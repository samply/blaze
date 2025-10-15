(ns blaze.interaction.history.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.db.api :as d]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.module :as m]
   [blaze.util :as u :refer [str]]
   [reitit.core :as reitit])
  (:import
   [java.time Instant OffsetDateTime]
   [java.time.format DateTimeParseException]))

(set! *warn-on-reflection* true)

(defn since
  "Tries to parse a valid instant out of the `_since` query param.

  Returns nil on absent or invalid instant."
  {:arglists '([query-params])}
  [{v "_since"}]
  (some
   #(try
      (Instant/from (OffsetDateTime/parse %))
      (catch DateTimeParseException _))
   (u/to-seq v)))

(defn page-t
  "Returns the t (optional) to constrain the database in paging. Pages will
  start with a database as-of `page-t`."
  {:arglists '([query-params])}
  [{v "__page-t"}]
  (some fhir-util/parse-nat-long (u/to-seq v)))

(defn- nav-url
  "Returns a nav URL which points to a the start of the history.

  Uses `match` to generate a link based on the current path with appended
  `query-params`."
  {:arglists '([context query-params])}
  [{:blaze/keys [base-url] ::reitit/keys [match]} query-params]
  (let [path (reitit/match->path match (select-keys query-params ["_count"]))]
    (str base-url path)))

(defn self-link [{::search-util/keys [link] :as context} query-params]
  (link "self" (nav-url context query-params)))

(defn page-nav-url
  "Returns a nav URL which points to a page with it's first entry described by
  the specified values.

  Uses `match` to generate a link based on the current path with appended
  `query-params` and the extra paging params calculated from `page-t`, `type`
  and `id`."
  {:arglists
   '([context query-params page-t]
     [context query-params page-t id]
     [context query-params page-t type id])}
  [{:blaze/keys [base-url db] :keys [page-id-cipher page-match]} query-params
   page-t & more]
  (->> (cond-> (assoc query-params "__t" (str (d/t db)) "__page-t" (str page-t))
         (= 1 (count more))
         (assoc "__page-id" (first more))
         (= 2 (count more))
         (assoc "__page-type" (first more) "__page-id" (second more)))
       (decrypt-page-id/encrypt page-id-cipher)
       (page-match)
       (reitit/match->path)
       (str base-url)))

(defn- method [resource]
  ((-> resource meta :blaze.db/op)
   {:create #fhir/code "POST"
    :put #fhir/code "PUT"
    :delete #fhir/code "DELETE"}))

(defn- url [{:fhir/keys [type] :keys [id] :as resource}]
  (cond-> (name type)
    (-> resource meta :blaze.db/op #{:create} not)
    (str "/" id)))

(defn- status [resource]
  (let [meta (meta resource)]
    (cond
      (-> meta :blaze.db/op #{:create}) #fhir/string "201"
      (-> meta :blaze.db/op #{:delete}) #fhir/string "204"
      :else
      (if (= 1 (-> meta :blaze.db/num-changes))
        #fhir/string "201"
        #fhir/string "200"))))

(defn build-entry [context {:fhir/keys [type] :keys [id] :as resource}]
  (cond->
   {:fhir/type :fhir.Bundle/entry
    :fullUrl (type/uri (fhir-util/instance-url context (name type) id))
    :request
    {:fhir/type :fhir.Bundle.entry/request
     :method (method resource)
     :url (type/uri (url resource))}
    :response
    {:fhir/type :fhir.Bundle.entry/response
     :status (status resource)
     :etag (type/string (str "W/\"" (-> resource :meta :versionId type/value) "\""))
     :lastModified (-> resource meta :blaze.db/tx :blaze.db.tx/instant)}}
    (-> resource meta :blaze.db/op #{:delete} not)
    (assoc :resource resource)))

(defn- total-value [total]
  (type/unsignedInt
   (if (< total (bit-shift-left 1 31))
     total
     {:extension
      [(type/extension
        {:url "https://samply.github.io/blaze/fhir/StructureDefinition/grand-total"
         :value (type/string (str total))})]})))

(defn build-bundle [context total query-params]
  {:fhir/type :fhir/Bundle
   :id (m/luid context)
   :type #fhir/code "history"
   :total (total-value total)
   :link [(self-link context query-params)]})
