(ns blaze.handler.fhir.util-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.fhir.spec.generators :as fg]
   [blaze.fhir.spec.type :as type]
   [blaze.fhir.spec.type.system :as system]
   [blaze.fhir.util :as fu]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.handler.fhir.util-spec]
   [blaze.handler.util :as handler-util]
   [blaze.module.test-util :as mtu :refer [given-failed-future with-system]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [java-time.api :as time]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [ring.util.response :as ring])
  (:import
   [java.time ZoneId ZonedDateTime]
   [java.time.format DateTimeFormatter]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest t-test
  (testing "no query param"
    (is (nil? (fhir-util/t {}))))

  (testing "invalid query param"
    (are [t] (nil? (fhir-util/t {"__t" t}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (are [v t] (= t (fhir-util/t {"__t" v}))
      "1" 1
      ["<invalid>" "2"] 2
      ["3" "4"] 3)))

(deftest page-size-test
  (testing "no query param"
    (is (= 50 (fhir-util/page-size {}))))

  (testing "invalid query param"
    (are [size] (= 50 (fhir-util/page-size {"_count" size}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (are [v size] (= size (fhir-util/page-size {"_count" v}))
      "0" 0
      "1" 1
      "50" 50
      "500" 500
      "1000" 1000
      "10000" 10000
      ["<invalid>" "2"] 2
      ["0" "1"] 0
      ["3" "4"] 3))

  (testing "10000 is the maximum"
    (is (= 10000 (fhir-util/page-size {"_count" "10001"})))))

(deftest page-offset-test
  (testing "no query param"
    (is (zero? (fhir-util/page-offset {}))))

  (testing "invalid query param"
    (are [offset] (zero? (fhir-util/page-offset {"__page-offset" offset}))
      "<invalid>"
      "-1"
      ""))

  (testing "valid query param"
    (are [v offset] (= offset (fhir-util/page-offset {"__page-offset" v}))
      "0" 0
      "1" 1
      "10" 10
      "100" 100
      "1000" 1000
      ["<invalid>" "2"] 2
      ["0" "1"] 0
      ["3" "4"] 3)))

(deftest page-type-test
  (testing "no query param"
    (is (nil? (fhir-util/page-type {}))))

  (testing "invalid query param"
    (are [type] (nil? (fhir-util/page-type {"__page-type" type}))
      "<invalid>"
      ""))

  (testing "valid query param"
    (are [v type] (= type (fhir-util/page-type {"__page-type" v}))
      "A" "A"
      ["<invalid>" "A"] "A"
      ["A" "B"] "A")))

(deftest page-id-test
  (testing "no query param"
    (is (nil? (fhir-util/page-id {}))))

  (testing "invalid query param"
    (are [id] (nil? (fhir-util/page-id {"__page-id" id}))
      "<invalid>"
      ""))

  (testing "valid query param"
    (are [v id] (= id (fhir-util/page-id {"__page-id" v}))
      "0" "0"
      ["<invalid>" "a"] "a"
      ["A" "b"] "A")))

(def comma-with-spaces-gen
  (let [spaces #(apply str (repeat % " "))]
    (gen/let [pre (gen/choose 0 5)
              post (gen/choose 0 5)]
      (str (spaces pre) "," (spaces post)))))

(def fields-gen
  (gen/let [fields (gen/vector (gen/such-that (comp not empty?) gen/string-alphanumeric) 1 10)
            separators (gen/vector comma-with-spaces-gen (dec (count fields)))]
    {:vector (mapv keyword fields)
     :string (str/join (cons (first fields) (interleave separators (rest fields))))}))

(deftest summary-test
  (testing "no query param"
    (is (= :complete (fhir-util/summary {}))))

  (testing "non true query param"
    (are [summary] (= :complete (fhir-util/summary {"_summary" summary}))
      "<invalid>"
      ["<invalid>" "<invalid>"]
      "false"
      ["false" "false"]
      "data"
      "text"
      "count"))

  (testing "true query param"
    (are [summary] (= :summary (fhir-util/summary {"_summary" summary}))
      "true"
      ["<invalid>" "true"]
      ["false" "true"]
      ["true" "false"])))

(deftest elements-test
  (testing "_elements is not present"
    (are [x] (empty? (fhir-util/elements x))
      nil
      {}))

  (testing "_elements is present"
    (tu/satisfies-prop 100
      (prop/for-all [fields (gen/vector fields-gen)]
        (let [values (mapv :string fields)
              values (cond
                       (< 1 (count values)) values
                       (empty? values) ""
                       :else (first values))
              query-params {"_elements" values}]
          (= (set (fhir-util/elements query-params))
             (apply set/union (map (comp set :vector) fields))))))))

(deftest date-test
  (testing "missing"
    (are [query-params] (nil? (fhir-util/date query-params "start"))
      nil
      {}
      {"end" "2024"}))

  (testing "invalid"
    (given (fhir-util/date {"start" "invalid"} "start")
      ::anom/category := ::anom/incorrect
      ::anom/message := "The value `invalid` of the query param `start` is no valid date."))

  (testing "valid"
    (tu/satisfies-prop 1000
      (prop/for-all [name gen/string-alphanumeric
                     value fg/date-value]
        (let [query-params {name value}]
          (= (system/parse-date value) (fhir-util/date query-params name)))))))

(def router
  (reitit/router
   [[""
     {}
     ["/Patient" {:name :Patient/type}]
     ["/Patient/{id}" {:name :Patient/instance}]
     ["/Patient/{id}/_history/{vid}" {:name :Patient/versioned-instance}]]]
   {:syntax :bracket
    :path "/fhir"}))

(def context
  {:blaze/base-url "http://localhost:8080"
   ::reitit/router router})

(deftest type-url-test
  (is (= "http://localhost:8080/fhir/Patient"
         (fhir-util/type-url context "Patient"))))

(deftest instance-url-test
  (is (= "http://localhost:8080/fhir/Patient/0"
         (fhir-util/instance-url context "Patient" "0"))))

(deftest versioned-instance-url-test
  (is (= "http://localhost:8080/fhir/Patient/0/_history/1"
         (fhir-util/versioned-instance-url context "Patient" "0" "1"))))

(deftest last-modified-test
  (tu/satisfies-prop 1000
    (prop/for-all [{:blaze.db.tx/keys [instant] :as tx} (s/gen :blaze.db/tx)]
      (= (->> (ZonedDateTime/ofInstant instant (ZoneId/of "GMT"))
              (.format DateTimeFormatter/RFC_1123_DATE_TIME))
         (fhir-util/last-modified tx)))))

(deftest etag-test
  (tu/satisfies-prop 1000
    (prop/for-all [{:blaze.db/keys [t] :as tx} (s/gen :blaze.db/tx)]
      (= (format "W/\"%d\"" t) (fhir-util/etag tx)))))

(deftest pull-test
  (testing "not-found"
    (with-system [{:blaze.db/keys [node]} mem-node-config]
      (given-failed-future (fhir-util/pull (d/db node) "Patient" "0")
        ::anom/category := ::anom/not-found
        ::anom/message := "Resource `Patient/0` was not found.")))

  (testing "deleted"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (given-failed-future (fhir-util/pull (d/db node) "Patient" "0")
        ::anom/category := ::anom/not-found
        ::anom/message := "Resource `Patient/0` was deleted."
        :http/status := 410
        :http/headers := [["Last-Modified" "Thu, 1 Jan 1970 00:00:00 GMT"]
                          ["ETag" "W/\"2\""]]
        :fhir/issue := "deleted")))

  (testing "found"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (given @(mtu/assoc-thread-name (fhir-util/pull (d/db node) "Patient" "0"))
        [meta :thread-name] :? mtu/common-pool-thread?
        :fhir/type := :fhir/Patient
        :id := "0"))

    (testing "summary variant"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/CodeSystem :id "0"
                 :url #fhir/uri "system-115910"
                 :version #fhir/string "version-170327"
                 :content #fhir/code "complete"
                 :concept
                 [{:fhir/type :fhir.CodeSystem/concept
                   :code #fhir/code "code-115927"}]}]]]

        (given @(mtu/assoc-thread-name (fhir-util/pull (d/db node) "CodeSystem" "0" :summary))
          [meta :thread-name] :? mtu/common-pool-thread?
          :fhir/type := :fhir/CodeSystem
          :id := "0"
          :concept := nil))))

  (testing "pull error"
    (with-redefs
     [d/pull (fn [_ _ _] (ac/completed-future (ba/fault)))]
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (given-failed-future (fhir-util/pull (d/db node) "Patient" "0")
          ::anom/category := ::anom/fault
          :fhir/issue := "incomplete")))))

(deftest pull-historic-test
  (testing "not-found"
    (with-system [{:blaze.db/keys [node]} mem-node-config]
      (given-failed-future (fhir-util/pull-historic (d/db node) "Patient" "0" 0)
        ::anom/category := ::anom/not-found
        ::anom/message := "Resource `Patient/0` with version `0` was not found."))

    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]
       [[:delete-history "Patient" "0"]]]

      (given-failed-future (fhir-util/pull-historic (d/db node) "Patient" "0" 1)
        ::anom/category := ::anom/not-found
        ::anom/message := "Resource `Patient/0` with version `1` was not found.")))

  (testing "found"
    (with-system-data [{:blaze.db/keys [node]} mem-node-config]
      [[[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean false}]]
       [[:put {:fhir/type :fhir/Patient :id "0" :active #fhir/boolean true}]]]

      (testing "version 1"
        (given @(mtu/assoc-thread-name (fhir-util/pull-historic (d/db node) "Patient" "0" 1))
          [meta :thread-name] :? mtu/common-pool-thread?
          :fhir/type := :fhir/Patient
          :id := "0"
          :active := #fhir/boolean false))

      (testing "version 2"
        (given @(mtu/assoc-thread-name (fhir-util/pull-historic (d/db node) "Patient" "0" 2))
          [meta :thread-name] :? mtu/common-pool-thread?
          :fhir/type := :fhir/Patient
          :id := "0"
          :active := #fhir/boolean true)))

    (testing "deleted version"
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:delete "Patient" "0"]]]

        (given-failed-future (fhir-util/pull-historic (d/db node) "Patient" "0" 1)
          ::anom/category := ::anom/not-found
          ::anom/message := "Resource `Patient/0` was deleted in version `1`."
          :http/status := 410
          :http/headers := [["Last-Modified" "Thu, 1 Jan 1970 00:00:00 GMT"]
                            ["ETag" "W/\"1\""]]
          :fhir/issue := "deleted"))))

  (testing "pull error"
    (with-redefs
     [d/pull (fn [_ _ _] (ac/completed-future (ba/fault)))]
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

        (given-failed-future (fhir-util/pull-historic (d/db node) "Patient" "0" 1)
          ::anom/category := ::anom/fault
          :fhir/issue := "incomplete")))))

(deftest match-type-id-test
  (is (= ["Patient" "164325"] (fhir-util/match-type-id "Patient/164325")))

  (are [url] (nil? (fhir-util/match-type-id url))
    "Patient"
    "Patient/"
    "Patient?"))

(deftest match-type-query-params-test
  (is (= ["Patient" nil] (fhir-util/match-type-query-params "Patient")))
  (is (= ["Patient" ""] (fhir-util/match-type-query-params "Patient?")))
  (is (= ["Patient" "name-164520=value-164527"] (fhir-util/match-type-query-params "Patient?name-164520=value-164527")))

  (are [url] (nil? (fhir-util/match-type-query-params url))
    ""
    "a"
    "0"))

(deftest match-url-test
  (testing "system-level"
    (is (nil? (fhir-util/match-url ""))))

  (testing "type-level"
    (is (= {:type "Patient"} (fhir-util/match-url "Patient")))

    (testing "operation"
      (is (= {:type "Measure" :kind :operation} (fhir-util/match-url "Measure/$evaluate-measure")))))

  (testing "instance-level"
    (is (= {:type "Patient" :id "135825"} (fhir-util/match-url "Patient/135825")))

    (testing "operation"
      (is (= {:type "Measure" :id "172940" :kind :operation} (fhir-util/match-url "Measure/172940/$evaluate-measure"))))))

(deftest validate-entry-test
  (testing "missing request"
    (satisfies-prop 10
      (prop/for-all [idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry})
           {::anom/category ::anom/incorrect
            ::anom/message "Missing request."
            :fhir/issue "value"
            :fhir.issue/expression (format "Bundle.entry[%d]" idx)}))))

  (testing "missing request URL"
    (satisfies-prop 10
      (prop/for-all [idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request}})
           {::anom/category ::anom/incorrect
            ::anom/message "Missing request URL."
            :fhir/issue "value"
            :fhir.issue/expression (format "Bundle.entry[%d].request" idx)}))))

  (testing "missing request method"
    (satisfies-prop 10
      (prop/for-all [idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :url #fhir/uri "foo"}})
           {::anom/category ::anom/incorrect
            ::anom/message "Missing request method."
            :fhir/issue "value"
            :fhir.issue/expression (format "Bundle.entry[%d].request" idx)}))))

  (testing "unknown request method"
    (satisfies-prop 10
      (prop/for-all [method (gen/such-that (complement #{"GET" "HEAD" "POST" "PUT" "DELETE" "PATCH"}) gen/string)
                     idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method (type/code method)
                                                    :url #fhir/uri "foo"}})
           {::anom/category ::anom/incorrect
            ::anom/message (format "Unknown request method `%s`." method)
            :fhir/issue "value"
            :fhir.issue/expression (format "Bundle.entry[%d].request.method" idx)}))))

  (testing "unsupported request method"
    (satisfies-prop 10
      (prop/for-all [idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method #fhir/code "PATCH"
                                                    :url #fhir/uri "foo"}})
           {::anom/category ::anom/unsupported
            ::anom/message "Unsupported request method `PATCH`."
            :fhir/issue "not-supported"
            :fhir.issue/expression (format "Bundle.entry[%d].request.method" idx)}))))

  (testing "missing request URL type"
    (satisfies-prop 10
      (prop/for-all [url (gen/fmap (partial str "missing-type-") gen/nat)
                     idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method #fhir/code "GET"
                                                    :url (type/uri url)}})
           {::anom/category ::anom/incorrect
            ::anom/message (format "Can't parse type from request URL `%s`." url)
            :fhir/issue "value"
            :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)}))))

  (testing "unknown request URL type"
    (satisfies-prop 10
      (prop/for-all [url (gen/fmap (partial str "UnknownType/") gen/nat)
                     idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method #fhir/code "GET"
                                                    :url (type/uri url)}})
           {::anom/category ::anom/incorrect
            ::anom/message (format "Unknown type `UnknownType` in bundle entry request URL `%s`." url)
            :fhir/issue "value"
            :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)}))))

  (testing "missing resource type"
    (satisfies-prop 10
      (prop/for-all [method (gen/elements ["POST" "PUT"])
                     idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method (type/code method)
                                                    :url #fhir/uri "Patient"}})
           {::anom/category ::anom/incorrect
            ::anom/message "Missing resource type."
            :fhir/issue "required"
            :fhir.issue/expression (format "Bundle.entry[%d].resource" idx)}))))

  (testing "type mismatch"
    (satisfies-prop 10
      (prop/for-all [method (gen/elements ["POST" "PUT"])
                     idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method (type/code method)
                                                    :url #fhir/uri "Patient"}
                                          :resource {:fhir/type :fhir/Observation}})
           {::anom/category ::anom/incorrect
            ::anom/message "Type mismatch between resource type `Observation` and URL `Patient`."
            :fhir/issue "invariant"
            :fhir.issue/expression
            [(format "Bundle.entry[%d].request.url" idx)
             (format "Bundle.entry[%d].resource.resourceType" idx)]
            :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"}))))

  (testing "operation type mismatch"
    (satisfies-prop 10
      (prop/for-all [idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method #fhir/code "POST"
                                                    :url #fhir/uri "Measure/$evaluate-measure"}
                                          :resource {:fhir/type :fhir/Observation}})
           {::anom/category ::anom/incorrect
            ::anom/message "Type mismatch between resource type `Observation` and URL `Measure/$evaluate-measure`."
            :fhir/issue "invariant"
            :fhir.issue/expression
            [(format "Bundle.entry[%d].request.url" idx)
             (format "Bundle.entry[%d].resource.resourceType" idx)]
            :fhir/operation-outcome "MSG_RESOURCE_TYPE_MISMATCH"}))))

  (testing "missing request URL id"
    (satisfies-prop 10
      (prop/for-all [type (gen/elements ["Patient" "Observation"])
                     idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method #fhir/code "PUT"
                                                    :url (type/uri type)}
                                          :resource {:fhir/type (keyword "fhir" type)}})
           {::anom/category ::anom/incorrect
            ::anom/message (format "Can't parse id from URL `%s`." type)
            :fhir/issue "value"
            :fhir.issue/expression (format "Bundle.entry[%d].request.url" idx)}))))

  (testing "missing resource id"
    (satisfies-prop 10
      (prop/for-all [type (gen/elements ["Patient" "Observation"])
                     id (s/gen :blaze.resource/id)
                     idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method #fhir/code "PUT"
                                                    :url (type/uri (str type "/" id))}
                                          :resource {:fhir/type (keyword "fhir" type)}})
           {::anom/category ::anom/incorrect
            ::anom/message "Resource id is missing."
            :fhir/issue "required"
            :fhir.issue/expression (format "Bundle.entry[%d].resource.id" idx)
            :fhir/operation-outcome "MSG_RESOURCE_ID_MISSING"}))))

  (testing "id mismatch"
    (satisfies-prop 10
      (prop/for-all [type (gen/elements ["Patient" "Observation"])
                     [url-id resource-id] (gen/such-that
                                           (partial apply not=)
                                           (gen/tuple (s/gen :blaze.resource/id)
                                                      (s/gen :blaze.resource/id)))
                     idx gen/nat]
        (let [url (str type "/" url-id)]
          (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                            :request {:fhir/type :fhir.Bundle.entry/request
                                                      :method #fhir/code "PUT"
                                                      :url (type/uri url)}
                                            :resource {:fhir/type (keyword "fhir" type)
                                                       :id resource-id}})
             {::anom/category ::anom/incorrect
              ::anom/message (format "Id mismatch between resource id `%s` and URL `%s`." resource-id url)
              :fhir/issue "invariant"
              :fhir.issue/expression
              [(format "Bundle.entry[%d].request.url" idx)
               (format "Bundle.entry[%d].resource.id" idx)]
              :fhir/operation-outcome "MSG_RESOURCE_ID_MISMATCH"})))))

  (testing "SUBSETTED create"
    (satisfies-prop 10
      (prop/for-all [idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method #fhir/code "PUT"
                                                    :url #fhir/uri "Patient"}
                                          :resource {:fhir/type :fhir/Patient
                                                     :meta (type/meta {:tag [fu/subsetted]})}})
           {::anom/category ::anom/incorrect
            ::anom/message "Resources with tag SUBSETTED may be incomplete and so can't be used in updates."
            :fhir/issue "processing"
            :fhir.issue/expression (format "Bundle.entry[%d].resource" idx)}))))

  (testing "SUBSETTED update"
    (satisfies-prop 10
      (prop/for-all [id (s/gen :blaze.resource/id)
                     idx gen/nat]
        (= (fhir-util/validate-entry idx {:fhir/type :fhir.Bundle/entry
                                          :request {:fhir/type :fhir.Bundle.entry/request
                                                    :method #fhir/code "PUT"
                                                    :url (type/uri (str "Patient/" id))}
                                          :resource {:fhir/type :fhir/Patient :id id
                                                     :meta (type/meta {:tag [fu/subsetted]})}})
           {::anom/category ::anom/incorrect
            ::anom/message "Resources with tag SUBSETTED may be incomplete and so can't be used in updates."
            :fhir/issue "processing"
            :fhir.issue/expression (format "Bundle.entry[%d].resource" idx)}))))

  (testing "metadata GET request"
    (let [entry {:fhir/type :fhir.Bundle/entry
                 :request {:fhir/type :fhir.Bundle.entry/request
                           :method #fhir/code "GET"
                           :url #fhir/uri "metadata"}}]
      (is (= entry (fhir-util/validate-entry 0 entry)))))

  (testing "Patient GET request"
    (let [entry {:fhir/type :fhir.Bundle/entry
                 :request {:fhir/type :fhir.Bundle.entry/request
                           :method #fhir/code "GET"
                           :url #fhir/uri "Patient"}}]
      (is (= entry (fhir-util/validate-entry 0 entry)))))

  (testing "Measure/$evaluate-measure POST request"
    (let [entry {:fhir/type :fhir.Bundle/entry
                 :request {:fhir/type :fhir.Bundle.entry/request
                           :method #fhir/code "POST"
                           :url #fhir/uri "Measure/$evaluate-measure"}
                 :resource {:fhir/type :fhir/Parameters}}]
      (is (= entry (fhir-util/validate-entry 0 entry))))))

(deftest process-batch-entry-test
  (testing "missing request"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)
                     idx gen/nat]
        (= @(fhir-util/process-batch-entry
             {:batch-handler (comp ac/completed-future ring/response)
              :blaze/base-url base-url}
             idx
             {:fhir/type :fhir.Bundle/entry})
           {:fhir/type :fhir.Bundle/entry
            :response {:fhir/type :fhir.Bundle.entry/response
                       :status #fhir/string "400"
                       :outcome {:fhir/type :fhir/OperationOutcome
                                 :issue [{:fhir/type :fhir.OperationOutcome/issue
                                          :severity #fhir/code "error"
                                          :code #fhir/code "value"
                                          :diagnostics #fhir/string "Missing request."
                                          :expression [(type/string (format "Bundle.entry[%d]" idx))]}]}}}))))

  (testing "error from batch-handler"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)
                     error-msg gen/string
                     idx gen/nat]
        (= @(fhir-util/process-batch-entry
             {:batch-handler (fn [_] (ac/completed-future (ba/fault error-msg)))
              :blaze/base-url base-url}
             idx
             {:fhir/type :fhir.Bundle/entry
              :request {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "GET"
                        :url #fhir/uri "Patient"}})
           {:fhir/type :fhir.Bundle/entry
            :response {:fhir/type :fhir.Bundle.entry/response
                       :status #fhir/string "500"
                       :outcome {:fhir/type :fhir/OperationOutcome
                                 :issue [{:fhir/type :fhir.OperationOutcome/issue
                                          :severity #fhir/code "error"
                                          :code #fhir/code "exception"
                                          :diagnostics (type/string error-msg)
                                          :expression [(type/string (format "Bundle.entry[%d]" idx))]}]}}}))))

  (testing "Measure/$evaluate-measure POST request"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)
                     context-path (gen/elements [nil "" "/fhir" "/other"])
                     idx gen/nat
                     location gen/string-alphanumeric]
        (= @(fhir-util/process-batch-entry
             (cond->
              {:batch-handler
               (fn [{:keys [headers]}]
                 (ac/completed-future
                  (ring/created
                   location
                   {:fhir/type :fhir/MeasureReport
                    :extension
                    [(type/extension
                      {:url "https://samply.github.io/blaze/fhir/StructureDefinition/return-preference"
                       :value (type/code (name (handler-util/preference headers "return")))})]})))
               :blaze/base-url base-url}
               context-path (assoc :context-path context-path))
             idx
             {:fhir/type :fhir.Bundle/entry
              :request {:fhir/type :fhir.Bundle.entry/request
                        :extension
                        [#fhir/Extension
                          {:url "https://samply.github.io/blaze/fhir/StructureDefinition/return-preference"
                           :value #fhir/code "representation"}]
                        :method #fhir/code "POST"
                        :url #fhir/uri "Measure/$evaluate-measure"}
              :resource {:fhir/type :fhir/Parameters}})
           {:fhir/type :fhir.Bundle/entry
            :response
            {:fhir/type :fhir.Bundle.entry/response
             :status #fhir/string "201"
             :location (type/uri location)}
            :resource
            {:fhir/type :fhir/MeasureReport
             :extension
             [#fhir/Extension
               {:url "https://samply.github.io/blaze/fhir/StructureDefinition/return-preference"
                :value #fhir/code "representation"}]}}))))

  (testing "Patient DELETE request"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)
                     context-path (gen/elements [nil "" "/fhir" "/other"])
                     idx gen/nat]
        (= @(fhir-util/process-batch-entry
             (cond->
              {:batch-handler (constantly (ac/completed-future (ring/status 204)))
               :blaze/base-url base-url}
               context-path (assoc :context-path context-path))
             idx
             {:fhir/type :fhir.Bundle/entry
              :request {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "DELETE"
                        :url #fhir/uri "Patient"}
              :resource {:fhir/type :fhir/Parameters}})
           {:fhir/type :fhir.Bundle/entry
            :response {:fhir/type :fhir.Bundle.entry/response
                       :status #fhir/string "204"}}))))

  (testing "Observation?code=code-100815 GET request"
    (st/unstrument `fhir-util/process-batch-entry)
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)
                     context-path (gen/elements [nil "" "/fhir" "/other"])
                     idx gen/nat]
        (= @(fhir-util/process-batch-entry
             (cond->
              {:batch-handler (comp ac/completed-future ring/response)
               :blaze/base-url base-url
               :blaze/db ::db}
               context-path
               (assoc :context-path context-path))
             idx
             {:fhir/type :fhir.Bundle/entry
              :request {:fhir/type :fhir.Bundle.entry/request
                        :method #fhir/code "GET"
                        :url #fhir/uri "Observation?code=code-100815"}})
           {:fhir/type :fhir.Bundle/entry
            :response {:fhir/type :fhir.Bundle.entry/response
                       :status #fhir/string "200"}
            :resource {:blaze/base-url base-url
                       :blaze/db ::db
                       :request-method :get
                       :uri (str context-path "/Observation")
                       :query-string "code=code-100815"}})))
    (st/instrument `fhir-util/process-batch-entry))

  (testing "Patient/<id> PUT request"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)
                     context-path (gen/elements [nil "" "/fhir" "/other"])
                     return-preference (gen/elements [:blaze.preference.return/minimal
                                                      :blaze.preference.return/representation
                                                      nil])
                     id (s/gen :blaze.resource/id)
                     if-match (gen/one-of [gen/string (gen/return nil)])
                     if-none-match (gen/one-of [gen/string (gen/return nil)])
                     if-none-exist (gen/one-of [gen/string (gen/return nil)])
                     tx (s/gen :blaze.db/tx)
                     location gen/string-alphanumeric
                     idx gen/nat]
        (let [{:keys [response resource] :fhir/keys [type]}
              @(fhir-util/process-batch-entry
                (cond->
                 {:batch-handler
                  (fn [request]
                    (-> (ring/response request)
                        (ring/header "Last-Modified" (fhir-util/last-modified tx))
                        (ring/header "ETag" (fhir-util/etag tx))
                        (ring/header "Location" location)
                        (ac/completed-future)))
                  :blaze/base-url base-url}
                  context-path
                  (assoc :context-path context-path)
                  return-preference
                  (assoc :blaze.preference/return return-preference))
                idx
                {:fhir/type :fhir.Bundle/entry
                 :request (cond->
                           {:fhir/type :fhir.Bundle.entry/request
                            :method #fhir/code "PUT"
                            :url (type/uri (str "Patient/" id))}
                            if-match
                            (assoc :ifMatch (type/string if-match))
                            if-none-match
                            (assoc :ifNoneMatch (type/string if-none-match))
                            if-none-exist
                            (assoc :ifNoneExist (type/string if-none-exist)))
                 :resource {:fhir/type :fhir/Patient :id id}})]
          (and (= type :fhir.Bundle/entry)
               (= response
                  {:fhir/type :fhir.Bundle.entry/response
                   :status #fhir/string "200"
                   :lastModified (time/truncate-to (:blaze.db.tx/instant tx) :seconds)
                   :etag (type/string (fhir-util/etag tx))
                   :location (type/uri location)})
               (= resource
                  (cond->
                   {:blaze/base-url base-url
                    :request-method :put
                    :uri (str context-path "/Patient/" id)
                    :headers {"prefer" "return=minimal"}
                    :body {:fhir/type :fhir/Patient :id id}}
                    if-match
                    (assoc-in [:headers "if-match"] if-match)
                    if-none-match
                    (assoc-in [:headers "if-none-match"] if-none-match)
                    if-none-exist
                    (assoc-in [:headers "if-none-exist"] if-none-exist)
                    return-preference
                    (assoc-in [:headers "prefer"] (str "return=" (name return-preference))))))))))

  (testing "cancel"
    (given-failed-future (fhir-util/process-batch-entry
                          {:batch-handler
                           (fn [{:blaze/keys [cancelled?]}]
                             (ac/completed-future (cancelled?)))
                           :blaze/base-url "foo"
                           :blaze/cancelled?
                           (constantly (ba/interrupted "msg-152801"))}
                          0
                          {:fhir/type :fhir.Bundle/entry
                           :request {:fhir/type :fhir.Bundle.entry/request
                                     :method #fhir/code "GET"
                                     :url #fhir/uri "Patient"}})
      ::anom/category := ::anom/interrupted
      ::anom/message := "msg-152801")))

(deftest process-batch-entries-test
  (testing "no entry"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)]
        (empty? @(fhir-util/process-batch-entries
                  {:batch-handler (comp ac/completed-future ring/response)
                   :blaze/base-url base-url}
                  [])))))

  (testing "Patient GET request"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)
                     context-path (gen/elements [nil "" "/fhir" "/other"])]
        (= @(fhir-util/process-batch-entries
             (cond->
              {:batch-handler (comp ac/completed-future ring/response)
               :blaze/base-url base-url}
               context-path
               (assoc :context-path context-path))
             [{:fhir/type :fhir.Bundle/entry
               :request {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "GET"
                         :url #fhir/uri "Patient"}}])
           [{:fhir/type :fhir.Bundle/entry
             :response {:fhir/type :fhir.Bundle.entry/response
                        :status #fhir/string "200"}
             :resource {:blaze/base-url base-url
                        :request-method :get
                        :uri (str context-path "/Patient")}}]))))

  (testing "processing isn't bound to stack size"
    (let [num-entries 100000]
      (given @(fhir-util/process-batch-entries
               {:batch-handler (fn [_] (ac/completed-future (ring/response nil)))
                :blaze/base-url ""
                :context-path "/fhir"}
               (repeat
                num-entries
                {:fhir/type :fhir.Bundle/entry
                 :request {:fhir/type :fhir.Bundle.entry/request
                           :method #fhir/code "GET"
                           :url #fhir/uri "Patient"}}))
        count := num-entries)))

  (testing "missing request"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)]
        (= @(fhir-util/process-batch-entries
             {:batch-handler (comp ac/completed-future ring/response)
              :blaze/base-url base-url}
             [{:fhir/type :fhir.Bundle/entry}])
           [{:fhir/type :fhir.Bundle/entry
             :response {:fhir/type :fhir.Bundle.entry/response
                        :status #fhir/string "400"
                        :outcome {:fhir/type :fhir/OperationOutcome
                                  :issue [{:fhir/type :fhir.OperationOutcome/issue
                                           :severity #fhir/code "error"
                                           :code #fhir/code "value"
                                           :diagnostics #fhir/string "Missing request."
                                           :expression [#fhir/string "Bundle.entry[0]"]}]}}}]))))

  (testing "missing request and Patient GET request"
    (satisfies-prop 10
      (prop/for-all [base-url (s/gen :blaze/base-url)]
        (= @(fhir-util/process-batch-entries
             {:batch-handler (comp ac/completed-future ring/response)
              :blaze/base-url base-url}
             [{:fhir/type :fhir.Bundle/entry}
              {:fhir/type :fhir.Bundle/entry
               :request {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "GET"
                         :url #fhir/uri "Patient"}}])
           [{:fhir/type :fhir.Bundle/entry
             :response {:fhir/type :fhir.Bundle.entry/response
                        :status #fhir/string "400"
                        :outcome {:fhir/type :fhir/OperationOutcome
                                  :issue [{:fhir/type :fhir.OperationOutcome/issue
                                           :severity #fhir/code "error"
                                           :code #fhir/code "value"
                                           :diagnostics #fhir/string "Missing request."
                                           :expression [#fhir/string "Bundle.entry[0]"]}]}}}
            {:fhir/type :fhir.Bundle/entry
             :response {:fhir/type :fhir.Bundle.entry/response
                        :status #fhir/string "200"}
             :resource {:blaze/base-url base-url
                        :request-method :get
                        :uri "/Patient"}}]))))

  (testing "cancel"
    (given-failed-future (fhir-util/process-batch-entries
                          {:batch-handler
                           (fn [{:blaze/keys [cancelled?]}]
                             (ac/completed-future (cancelled?)))
                           :blaze/base-url "foo"
                           :blaze/cancelled?
                           (constantly (ba/interrupted "msg-152801"))}
                          [{:fhir/type :fhir.Bundle/entry
                            :request {:fhir/type :fhir.Bundle.entry/request
                                      :method #fhir/code "GET"
                                      :url #fhir/uri "Patient"}}])
      ::anom/category := ::anom/interrupted
      ::anom/message := "msg-152801")))
