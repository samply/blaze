(ns blaze.fhir-client-test
  (:require
   [blaze.async.flow :as flow]
   [blaze.fhir-client :as fhir-client]
   [blaze.fhir-client-spec]
   [blaze.fhir.parsing-context]
   [blaze.fhir.spec.type]
   [blaze.fhir.test-util :refer [structure-definition-repo]]
   [blaze.fhir.writing-context]
   [blaze.module.test-util :refer [given-failed-future]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [cognitect.anomalies :as anom]
   [integrant.core :as ig]
   [jsonista.core :as j]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log])
  (:import
   [com.pgssoft.httpclient Condition HttpClientMock]
   [java.nio.file Files Path]
   [java.nio.file.attribute FileAttribute]))

(set! *warn-on-reflection* true)
(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def ^:private system
  (ig/init
   {:blaze.fhir/parsing-context
    {:structure-definition-repo structure-definition-repo}
    :blaze.fhir/writing-context
    {:structure-definition-repo structure-definition-repo}}))

(defn- opts []
  {:http-client (HttpClientMock.)
   :parsing-context (:blaze.fhir/parsing-context system)
   :writing-context (:blaze.fhir/writing-context system)})

(deftest metadata-test
  (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

    (-> (.onGet http-client "http://localhost:8080/fhir/metadata")
        (.doReturn (j/write-value-as-string {:resourceType "CapabilityStatement"}))
        (.withHeader "content-type" "application/fhir+json"))

    (given @(fhir-client/metadata "http://localhost:8080/fhir" opts)
      :fhir/type := :fhir/CapabilityStatement)))

(deftest read-test
  (testing "success"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn (j/write-value-as-string {:resourceType "Patient" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/read "http://localhost:8080/fhir" "Patient" "0"
                                opts)
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "with application/json Content-Type"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn (j/write-value-as-string {:resourceType "Patient" :id "0"}))
          (.withHeader "content-type" "application/json"))

      (given @(fhir-client/read "http://localhost:8080/fhir" "Patient" "0"
                                opts)
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "not-found"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn
           404
           (j/write-value-as-string
            {:resourceType "OperationOutcome"
             :issue
             [{:severity "error"
               :code "not-found"}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given-failed-future (fhir-client/read "http://localhost:8080/fhir"
                                             "Patient" "0"
                                             opts)
        ::anom/category := ::anom/not-found
        [:fhir/issues 0 :severity] := #fhir/code"error"
        [:fhir/issues 0 :code] := #fhir/code"not-found")))

  (testing "Invalid JSON response"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn "{")
          (.withHeader "content-type" "application/json"))

      (given-failed-future (fhir-client/read "http://localhost:8080/fhir"
                                             "Patient" "0"
                                             opts)
        ::anom/category := ::anom/fault
        ::anom/message :# "Unexpected end-of-input:(.|\\s)*")))

  (testing "Server Error without JSON response"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturnStatus 500))

      (given-failed-future (fhir-client/read "http://localhost:8080/fhir"
                                             "Patient" "0"
                                             opts)
        ::anom/category := ::anom/fault)))

  (testing "Service Unavailable without JSON response"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn 503 "Service Unavailable"))

      (given-failed-future (fhir-client/read "http://localhost:8080/fhir"
                                             "Patient" "0"
                                             opts)
        ::anom/category := ::anom/unavailable)))

  (testing "Gateway timeout without JSON response (external load-balancer)"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn 504 "Gateway Timeout"))

      (given-failed-future (fhir-client/read "http://localhost:8080/fhir"
                                             "Patient" "0"
                                             opts)
        ::anom/category := ::anom/busy)))

  (testing "Zero Response Code"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturnStatus 0))

      (given-failed-future (fhir-client/read "http://localhost:8080/fhir"
                                             "Patient" "0"
                                             opts)
        ::anom/category := ::anom/fault
        ::anom/message := "Unexpected response status 0."))))

(defn- empty-header-condition [name]
  (reify Condition
    (matches [_ request]
      (.isEmpty (.firstValue (.headers request) name)))))

(deftest create-test
  (testing "return location header value"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)
          resource {:fhir/type :fhir/Patient}]

      (-> (.onPost http-client "http://localhost:8080/fhir/Patient")
          (.doReturnStatus 201)
          (.withHeader "location" "http://localhost:8080/fhir/Patient/0"))

      (is (= @(fhir-client/create "http://localhost:8080/fhir" resource
                                  opts)
             "http://localhost:8080/fhir/Patient/0")))))

(deftest update-test
  (testing "without meta versionId"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)
          resource {:fhir/type :fhir/Patient :id "0"}]

      (-> (.onPut http-client "http://localhost:8080/fhir/Patient/0")
          (.with (empty-header-condition "If-Match"))
          (.doReturn (j/write-value-as-string {:resourceType "Patient" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/update "http://localhost:8080/fhir" resource
                                  opts)
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "with meta versionId"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)
          resource {:fhir/type :fhir/Patient :id "0"
                    :meta #fhir/Meta{:versionId #fhir/id"180040"}}]

      (-> (.onPut http-client "http://localhost:8080/fhir/Patient/0")
          (.withHeader "If-Match" "W/\"180040\"")
          (.doReturn (j/write-value-as-string {:resourceType "Patient" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/update "http://localhost:8080/fhir" resource
                                  opts)
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "stale update"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)
          resource {:fhir/type :fhir/Patient :id "0"
                    :meta #fhir/Meta{:versionId #fhir/id"180040"}}]

      (-> (.onPut http-client "http://localhost:8080/fhir/Patient/0")
          (.withHeader "If-Match" "W/\"180040\"")
          (.doReturn
           412
           (j/write-value-as-string
            {:resourceType "OperationOutcome"
             :issue
             [{:severity "error"}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given-failed-future (fhir-client/update "http://localhost:8080/fhir"
                                               resource
                                               opts)
        ::anom/category := ::anom/conflict
        [:fhir/issues 0 :severity] := #fhir/code"error"))))

(deftest delete-test
  (testing "204 No Content"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onDelete http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturnStatus 204))

      (is (nil? @(fhir-client/delete "http://localhost:8080/fhir" "Patient" "0"
                                     opts)))))

  (testing "200 with OperationOutcome"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onDelete http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn (j/write-value-as-string {:resourceType "OperationOutcome"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/delete "http://localhost:8080/fhir" "Patient" "0"
                                  opts)
        :fhir/type := :fhir/OperationOutcome))))

(deftest delete-history-test
  (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

    (-> (.onDelete http-client "http://localhost:8080/fhir/Patient/0/_history")
        (.doReturnStatus 204))

    (is (nil? @(fhir-client/delete-history "http://localhost:8080/fhir"
                                           "Patient" "0"
                                           opts)))))

(deftest transact-test
  (let [{:keys [^HttpClientMock http-client] :as opts} (opts)
        bundle {:fhir/type :fhir/Bundle
                :type #fhir/code"transaction"
                :entry
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient :id "0"}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code"PUT"
                   :url #fhir/uri"Patient/0"}}]}]

    (-> (.onPost http-client "http://localhost:8080/fhir")
        (.doReturn (j/write-value-as-string {:resourceType "Bundle"}))
        (.withHeader "content-type" "application/fhir+json"))

    (given @(fhir-client/transact "http://localhost:8080/fhir" bundle
                                  opts)
      :fhir/type := :fhir/Bundle)))

(deftest execute-type-get-test
  (testing "success"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/administrative-gender")
          (.doReturn (j/write-value-as-string {:resourceType "ValueSet" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/execute-type-get
               "http://localhost:8080/fhir" "ValueSet" "expand"
               (assoc opts :query-params {:url "http://hl7.org/fhir/ValueSet/administrative-gender"}))
        :fhir/type := :fhir/ValueSet
        :id := "0"))))

(deftest search-type-test
  (testing "one bundle with one patient"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-type "http://localhost:8080/fhir" "Patient"
                                       opts)
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0")))

  (testing "one bundle with two patients"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}
              {:resource {:resourceType "Patient" :id "1"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-type "http://localhost:8080/fhir" "Patient"
                                       opts)
        count := 2
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1")))

  (testing "two bundles with two patients"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :link
             [{:relation "next"
               :url "http://localhost:8080/fhir/Patient?page=2"}]
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient?page=2")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "1"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-type "http://localhost:8080/fhir" "Patient"
                                       opts)
        count := 2
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1")))

  (testing "with query params"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient?birthdate=2020")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-type "http://localhost:8080/fhir" "Patient"
                                       (assoc opts :query-params {:birthdate "2020"}))
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0")))

  (testing "Server Error without JSON response"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnStatus 500))

      (given-failed-future (fhir-client/search-type "http://localhost:8080/fhir"
                                                    "Patient"
                                                    opts)
        ::anom/category := ::anom/fault))))

(deftest search-system-test
  (testing "one bundle with one patient"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-system "http://localhost:8080/fhir"
                                         opts)
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0")))

  (testing "one bundle with two patients"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}
              {:resource {:resourceType "Patient" :id "1"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-system "http://localhost:8080/fhir"
                                         opts)
        count := 2
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1")))

  (testing "two bundles with two patients"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :link
             [{:relation "next"
               :url "http://localhost:8080/fhir?page=2"}]
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (-> (.onGet http-client "http://localhost:8080/fhir?page=2")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "1"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-system "http://localhost:8080/fhir"
                                         opts)
        count := 2
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1")))

  (testing "with query params"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir?_id=0")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-system "http://localhost:8080/fhir"
                                         (assoc opts :query-params {"_id" "0"}))
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0")))

  (testing "Server Error without JSON response"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir")
          (.doReturnStatus 500))

      (given-failed-future (fhir-client/search-system "http://localhost:8080/fhir"
                                                      opts)
        ::anom/category := ::anom/fault))))

(deftest history-instance-test
  (testing "one bundle with one patient"
    (let [{:keys [^HttpClientMock http-client] :as opts} (opts)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0/_history")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/history-instance "http://localhost:8080/fhir"
                                            "Patient" "0"
                                            opts)
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"))))

(def temp-dir (Files/createTempDirectory "blaze" (make-array FileAttribute 0)))

(deftest spit-test
  (testing "success"
    (let [{:keys [^HttpClientMock http-client writing-context] :as opts} (opts)
          publisher (fhir-client/search-type-publisher
                     "http://localhost:8080/fhir" "Patient"
                     opts)
          processor (fhir-client/resource-processor)
          future (fhir-client/spit writing-context temp-dir processor)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturn
           (j/write-value-as-string
            {:resourceType "Bundle"
             :entry
             [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (flow/subscribe! publisher processor)

      (is (= ["Patient-0.json"] (map #(str (.getFileName ^Path %)) @future)))))

  (testing "Server Error without JSON response"
    (let [{:keys [^HttpClientMock http-client writing-context] :as opts} (opts)
          publisher (fhir-client/search-type-publisher
                     "http://localhost:8080/fhir" "Patient"
                     opts)
          processor (fhir-client/resource-processor)
          future (fhir-client/spit writing-context temp-dir processor)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnStatus 500))

      (flow/subscribe! publisher processor)

      (given-failed-future future
        ::anom/category := ::anom/fault))))
