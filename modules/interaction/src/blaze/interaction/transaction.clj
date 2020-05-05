(ns blaze.interaction.transaction
  "FHIR batch/transaction interaction.

  https://www.hl7.org/fhir/http.html#transaction"
  (:require
    [blaze.bundle :as bundle]
    [blaze.db.api :as d]
    [blaze.executors :as ex]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.handler.fhir.util :as fhir-util]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.metrics :refer [wrap-observe-request-duration]]
    [blaze.uuid :refer [random-uuid]]
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [cognitect.anomalies :as anom]
    [integrant.core :as ig]
    [manifold.deferred :as md]
    [reitit.core :as reitit]
    [reitit.ring]
    [ring.util.response :as ring]
    [taoensso.timbre :as log])
  (:import
    [java.time.format DateTimeFormatter]))


(set! *warn-on-reflection* true)


(defn- validate-entry
  {:arglists '([db idx entry])}
  [idx {:keys [resource] {:keys [method url] :as request} :request :as entry}]
  (let [[type id] (some-> url bundle/match-url)]
    (cond
      (nil? request)
      {::anom/category ::anom/incorrect
       ::anom/message "Missing request."
       :fhir/issue "value"
       :fhir.issue/expression (format "Bundle.entry[%d]" idx)}

      (nil? url)
      {::anom/category ::anom/incorrect
       ::anom/message "Missing url."
       :fhir/issue "value"
       :fhir.issue/expression (format "Bundle.entry[%d].request" idx)}

      (nil? method)
      {::anom/category ::anom/incorrect
       ::anom/message "Missing method."
       :fhir/issue "value"
       :fhir.issue/expression (format "Bundle.entry[%d].request" idx)}

      (not (#{"GET" "HEAD" "POST" "PUT" "DELETE" "PATCH"} method))
      {::anom/category ::anom/incorrect
       ::anom/message (str "Unknown method `" method "`.")
       :fhir/issue "value"
       :fhir.issue/expression (format "Bundle.entry[%d].request.method" idx)}

      (not (#{"GET" "POST" "PUT" "DELETE"} method))
      {::anom/category ::anom/unsupported
       ::anom/message (str "Unsupported method `" method "`.")
       :fhir/issue "not-supported"
       :fhir.issue/expression (format "Bundle.entry[%d].request.method" idx)}

      (nil? type)
      {::anom/category ::anom/incorrect
       ::anom/message
       (format "Can't parse type from `entry.request.url` `%s`." url)
       :fhir/issue "value"
       :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)}

      (not (fhir-spec/type-exists? type))
      {::anom/category ::anom/incorrect
       ::anom/message
       (format "Unknown type `%s` in bundle entry URL `%s`." type url)
       :fhir/issue "value"
       :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)}

      (and (#{"POST" "PUT"} method) (not (map? resource)))
      {::anom/category ::anom/incorrect
       ::anom/message
       (format "Expected resource of entry %d to be a JSON Object." idx)
       :fhir/issue "structure"
       :fhir.issue/expression (format "Bundle.entry[%d].resource" idx)
       :fhir/operation-outcome "MSG_JSON_OBJECT"}

      (and (#{"POST" "PUT"} method) (not= type (:resourceType resource)))
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir.issue/expression
       [(format "Bundle.entry[%d].request.url" idx)
        (format "Bundle.entry[%d].resource.resourceType" idx)]
       :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"}

      (and (= "PUT" method) (nil? id))
      {::anom/category ::anom/incorrect
       ::anom/message "Can't parse id from `entry.request.url` `" url "`."
       :fhir/issue "value"}

      (and (= "PUT" method) (not (contains? resource :id)))
      {::anom/category ::anom/incorrect
       :fhir/issue "required"
       :fhir.issue/expression
       [(format "Bundle.entry[%d].resource.id" idx)]
       :fhir/operation-outcome "MSG_RESOURCE_ID_MISSING"}

      (and (= "PUT" method) (not (s/valid? :blaze.resource/id (:id resource))))
      {::anom/category ::anom/incorrect
       :fhir/issue "value"
       :fhir.issue/expression
       [(format "Bundle.entry[%d].resource.id" idx)]
       :fhir/operation-outcome "MSG_ID_INVALID"}

      (and (= "PUT" method) (not= id (:id resource)))
      {::anom/category ::anom/incorrect
       :fhir/issue "invariant"
       :fhir.issue/expression
       [(format "Bundle.entry[%d].request.url" idx)
        (format "Bundle.entry[%d].resource.id" idx)]
       :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"}

      (not (fhir-spec/valid? resource))
      {::anom/category ::anom/incorrect
       ::anom/message "Resource invalid."
       :fhir/issue "invariant"
       :fhir.issue/expression
       [(format "Bundle.entry[%d].resource" idx)]}

      :else
      (assoc entry :blaze/type type :blaze/id id))))


(defn- validate-entries
  [entries]
  (transduce
    (map-indexed vector)
    (completing
      (fn [res [idx entry]]
        (let [entry (validate-entry idx entry)]
          (if (::anom/category entry)
            (reduced entry)
            (conj res entry)))))
    []
    entries))


(defn- prepare-entry
  [db {{:keys [method]} :request :keys [resource] :as entry}]
  (log/trace "prepare-entry" method (:resourceType resource) (:id resource))
  (cond
    (= "PUT" method)
    (let [{type :resourceType id :id} resource]
      (assoc entry :blaze/resource-exists? (d/resource-exists? db type id)))

    (= "POST" method)
    (assoc-in entry [:resource :id] (str (random-uuid)))

    :else
    entry))


(defn- validate-and-prepare-bundle
  [db {:keys [resourceType type] entries :entry}]
  (cond
    (not= "Bundle" resourceType)
    (md/error-deferred
      {::anom/category ::anom/incorrect
       ::anom/message (str "Expected a Bundle but was `" resourceType "`.")
       :fhir/issue "value"})

    (not (#{"batch" "transaction"} type))
    (md/error-deferred
      {::anom/category ::anom/incorrect
       ::anom/message
       (str "Expected a Bundle type of batch or transaction but was `" type "`.")
       :fhir/issue "value"})

    :else
    (if (= "transaction" type)
      (let [entries (validate-entries entries)]
        (if (::anom/category entries)
          (md/error-deferred entries)
          (mapv #(prepare-entry db %) entries)))
      entries)))


(defmulti build-response-entry
  "Builds the response entry."
  {:arglists '([context request-entry db])}
  (fn [_ {{:keys [method]} :request} _] method))


(defn- last-modified [{:blaze.db.tx/keys [instant]}]
  (str instant))


(defmethod build-response-entry "POST"
  [{:keys [router return-preference]}
   {{type :resourceType id :id} :resource}
   db]
  (let [resource (d/resource db type id)
        {:blaze.db/keys [tx]} (meta resource)
        vid (str (:blaze.db/t tx))]
    (cond->
      {:response
       {:status "201"
        :etag (str "W/\"" vid "\"")
        :lastModified (last-modified tx)
        :location
        (fhir-util/versioned-instance-url router type id vid)}}

      (= "representation" return-preference)
      (assoc :resource resource))))


(defmethod build-response-entry "PUT"
  [{:keys [router return-preference]}
   {{type :resourceType id :id} :resource :blaze/keys [resource-exists?]}
   db]
  (let [resource (d/resource db type id)
        {:blaze.db/keys [tx]} (meta resource)
        vid (str (:blaze.db/t tx))]
    (cond->
      {:response
       (cond->
         {:status (if resource-exists? "200" "201")
          :etag (str "W/\"" vid "\"")
          :lastModified (last-modified tx)}
         (not resource-exists?)
         (assoc
           :location
           (fhir-util/versioned-instance-url router type id vid)))}

      (= "representation" return-preference)
      (assoc :resource resource))))


(defmethod build-response-entry "DELETE"
  [_ _ db]
  {:response
   {:status "204"
    :lastModified (last-modified (d/tx db (d/basis-t db)))}})


(defn- strip-leading-slash [s]
  (if (str/starts-with? s "/")
    (subs s 1)
    s))


(defn- convert-http-date
  "Converts string `s` representing a HTTP date into a FHIR instant formatted
  string."
  [s]
  (->> (.parse DateTimeFormatter/RFC_1123_DATE_TIME s)
       (.format DateTimeFormatter/ISO_INSTANT)))


(defn- process-batch-entry
  [{:keys [handler] :blaze/keys [context-path]}
   {{:keys [method url]} :request :keys [resource]}]
  (let [url (strip-leading-slash (str/trim url))
        [url query-string] (str/split url #"\?")]
    (if (= "" url)
      (handler-util/bundle-error-response
        {::anom/category ::anom/incorrect
         ::anom/message (format "Invalid URL `%s` in bundle request." url)
         :fhir/issue "value"})
      (let [request
            (cond->
              {:uri (str context-path "/" url)
               :request-method (keyword (str/lower-case method))}

              query-string
              (assoc :query-string query-string)

              resource
              (assoc :body resource))]
        (-> (handler request)
            (md/chain'
              (fn [{:keys [status body]
                    {etag "ETag"
                     last-modified "Last-Modified"
                     location "Location"}
                    :headers}]
                (cond->
                  {:response
                   (cond->
                     {:status (str status)}

                     etag
                     (assoc :etag etag)

                     last-modified
                     (assoc :lastModified (convert-http-date last-modified))

                     location
                     (assoc :location location))}

                  (and (#{200 201} status) body)
                  (assoc :resource body)

                  (<= 400 status)
                  (update :response assoc :outcome body)))))))))


(defmulti process
  "Processes the prepared entries according the batch or transaction rules and
  returns the response entries."
  {:arglists '([context type request-entries])}
  (fn [_ type _] type))


(defmethod process "batch"
  [context _ request-entries]
  (md/loop [[request-entry & request-entries] request-entries
            response-entries []]
    (if request-entry
      (if (:response request-entry)
        (md/recur request-entries (conj response-entries request-entry))
        (-> (process-batch-entry context request-entry)
            (md/chain'
              (fn [response-entry]
                (md/recur request-entries (conj response-entries response-entry))))
            (md/catch'
              (fn [error]
                (let [response (handler-util/bundle-error-response error)]
                  (md/recur request-entries (conj response-entries {:response response})))))))
      response-entries)))


(defmethod process "transaction"
  [{:keys [node] :as context} _ request-entries]
  (-> (d/submit-tx node (bundle/tx-ops request-entries))
      (md/chain'
        (fn [db]
          (mapv
            #(build-response-entry context % db)
            request-entries)))))


(defn- handler-intern [node executor]
  (fn [{{:keys [type] :as bundle} :body :keys [headers]
        ::reitit/keys [router match]}]
    (let [db (d/db node)]
      (md/future-with executor
        (-> (validate-and-prepare-bundle db bundle)
            (md/chain'
              (let [context
                    {:router router
                     :handler (reitit.ring/ring-handler router)
                     :blaze/context-path (-> match :data :blaze/context-path)
                     :node node
                     :return-preference (handler-util/preference headers "return")}]
                #(process context type %)))
            (md/chain'
              (fn [response-entries]
                (ring/response
                  {:resourceType "Bundle"
                   :type (str type "-response")
                   :entry response-entries})))
            (md/catch' handler-util/error-response))))))


(defn- wrap-interaction-name [handler]
  (fn [{{:keys [type]} :body :as request}]
    (-> (handler request)
        (md/chain'
          (fn [response]
            (assoc response :fhir/interaction-name type))))))


(defn handler [node executor]
  (-> (handler-intern node executor)
      (wrap-interaction-name)
      (wrap-observe-request-duration)))


(defmethod ig/init-key :blaze.interaction.transaction/handler
  [_ {:keys [node executor]}]
  (log/info "Init FHIR transaction interaction handler")
  (handler node executor))


(defmethod ig/init-key ::executor
  [_ _]
  (log/info "Init FHIR transaction interaction executor")
  (ex/cpu-bound-pool "transaction-interaction-%d"))


(derive ::executor :blaze.metrics/thread-pool-executor)
