(ns blaze.interaction.history.util
  (:refer-clojure :exclude [str])
  (:require
   [blaze.db.api :as d]
   [blaze.fhir.canonical :as canonical]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.middleware.fhir.page-id :as page-id]
   [blaze.module :as m]
   [blaze.util :as u :refer [str]]
   [reitit.core :as reitit]))

(set! *warn-on-reflection* true)

(defn page-t
  "Returns the t (optional) to constrain the database in paging. Pages will
  start with a database as-of `page-t`.

  Reads the paging param `__page-t`."
  {:arglists '([paging-params])}
  [{v "__page-t"}]
  (some fhir-util/parse-nat-long (u/to-seq v)))

(defn decode-params
  "Decodes the params of the history interactions.

  The paging params are only taken from `paging-params` which originate from a
  decrypted page id and never from `query-params`.

  Decoded params consist of:
   :page-size - the page size from `_count`
   :since - the instant from `_since` (optional)
   :summary - either :summary or :complete from `_summary`
   :page-t - the t of the first entry of the page (optional)
   :page-type - the type of the first entry of the page (optional)
   :page-id - the id of the first entry of the page (optional)"
  {:arglists '([query-params paging-params])}
  [query-params paging-params]
  {:page-size (fhir-util/page-size query-params)
   :since (fhir-util/since query-params)
   :summary (fhir-util/summary query-params)
   :page-t (page-t paging-params)
   :page-type (fhir-util/page-type paging-params)
   :page-id (fhir-util/page-id paging-params)})

(defn- query-params [{:keys [page-size since summary]}]
  (cond-> {"_count" (str page-size)}
    since (assoc "_since" (str since))
    (= :summary summary) (assoc "_summary" "true")))

(defn- nav-url
  "Returns a nav URL which points to a the start of the history.

  Uses `match` to generate a link based on the current path with appended
  query params created from the decoded `params`."
  [{:blaze/keys [base-url] ::reitit/keys [match]} params]
  (let [path (reitit/match->path match (query-params params))]
    (str base-url path)))

(defn- self-link [{::search-util/keys [link] :as context} params]
  (link "self" (nav-url context params)))

(defn page-nav-url
  "Returns a nav URL which points to a page with it's first entry described by
  the specified values.

  Uses `match` to generate a link based on the current path with appended
  query params created from the decoded `params` and the extra paging params
  calculated from `page-t`, `type` and `id`."
  {:arglists
   '([context params page-t]
     [context params page-t id]
     [context params page-t type id])}
  [{:blaze/keys [base-url db] :keys [page-id-cipher page-match]} params
   page-t & more]
  (->> (cond-> (assoc (query-params params)
                      "__t" (str (d/t db)) "__page-t" (str page-t))
         (= 1 (count more))
         (assoc "__page-id" (first more))
         (= 2 (count more))
         (assoc "__page-type" (first more) "__page-id" (second more)))
       (page-id/encrypt page-id-cipher)
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
     :etag (type/string (str "W/\"" (-> resource :meta :versionId :value) "\""))
     :lastModified (-> resource meta :blaze.db/tx handler-util/instant)}}
    (-> resource meta :blaze.db/op #{:delete} not)
    (assoc :resource resource)))

(defn- total-value [total]
  (type/unsignedInt
   (if (< total (bit-shift-left 1 31))
     total
     {:extension (canonical/extensions "StructureDefinition/grand-total"
                                       (type/string (str total)))})))

(defn build-bundle [context total params]
  {:fhir/type :fhir/Bundle
   :id (m/luid context)
   :type #fhir/code "history"
   :total (total-value total)
   :link [(self-link context params)]})

(defn build-page [page-size handles]
  (let [handles (into [] (take (inc page-size)) handles)]
    (if (< page-size (count handles))
      {:handles (pop handles)
       :next-handle (peek handles)}
      {:handles handles})))

(defn pull-opts [{:keys [summary page-t]}]
  (cond-> {:variant summary}
    page-t (assoc :skip-cache-insertion? true)))
