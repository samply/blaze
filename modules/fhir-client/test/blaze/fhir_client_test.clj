(ns blaze.fhir-client-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir-client :as fhir-client]
    [blaze.fhir-client-spec]
    [blaze.fhir.spec.type]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [jsonista.core :as j]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [com.pgssoft.httpclient HttpClientMock Condition]
    [java.nio.file Files Path]
    [java.nio.file.attribute FileAttribute]
    [org.hamcrest Matchers]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest metadata-test
  (let [http-client (HttpClientMock.)]

    (-> (.onGet http-client "http://localhost:8080/fhir/metadata")
        (.doReturn (j/write-value-as-string {:resourceType "CapabilityStatement"}))
        (.withHeader "content-type" "application/fhir+json"))

    (given @(fhir-client/metadata "http://localhost:8080/fhir"
                                  {:http-client http-client})
      :fhir/type := :fhir/CapabilityStatement)))


(deftest read-test
  (testing "success"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn (j/write-value-as-string {:resourceType "Patient" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/read "http://localhost:8080/fhir" "Patient" "0"
                                {:http-client http-client})
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "with application/json Content-Type"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn (j/write-value-as-string {:resourceType "Patient" :id "0"}))
          (.withHeader "content-type" "application/json"))

      (given @(fhir-client/read "http://localhost:8080/fhir" "Patient" "0"
                                {:http-client http-client})
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "not-found"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn
            404
            (j/write-value-as-string
              {:resourceType "OperationOutcome"
               :issue
               [{:severity "error"
                 :code "not-found"}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(-> (fhir-client/read "http://localhost:8080/fhir" "Patient" "0"
                                    {:http-client http-client})
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/not-found
        [:fhir/issues 0 :severity] := #fhir/code"error"
        [:fhir/issues 0 :code] := #fhir/code"not-found")))

  (testing "Server Error without JSON response"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturnStatus 500))

      (given @(-> (fhir-client/read "http://localhost:8080/fhir" "Patient" "0"
                                    {:http-client http-client})
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/fault)))

  (testing "Service Unavailable without JSON response"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn 503 "Service Unavailable"))

      (given @(-> (fhir-client/read "http://localhost:8080/fhir" "Patient" "0"
                                    {:http-client http-client})
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/unavailable)))

  (testing "Gateway timeout without JSON response (external load-balancer)"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn 504 "Gateway Timeout"))

      (given @(-> (fhir-client/read "http://localhost:8080/fhir" "Patient" "0"
                                    {:http-client http-client})
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/busy))))


(defn- empty-header-condition [name]
  (reify Condition
    (matches [_ request]
      (.isEmpty (.firstValue (.headers request) name)))))


(deftest update-test
  (testing "without meta versionId"
    (let [http-client (HttpClientMock.)
          resource {:fhir/type :fhir/Patient :id "0"}]

      (-> (.onPut http-client "http://localhost:8080/fhir/Patient/0")
          (.with (empty-header-condition "If-Match"))
          (.doReturn (j/write-value-as-string {:resourceType "Patient" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/update "http://localhost:8080/fhir" resource
                                  {:http-client http-client})
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "with meta versionId"
    (let [http-client (HttpClientMock.)
          resource {:fhir/type :fhir/Patient :id "0"
                    :meta #fhir/Meta{:versionId #fhir/id"180040"}}]

      (-> (.onPut http-client "http://localhost:8080/fhir/Patient/0")
          (.withHeader "If-Match" "W/\"180040\"")
          (.doReturn (j/write-value-as-string {:resourceType "Patient" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/update "http://localhost:8080/fhir" resource
                                  {:http-client http-client})
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "stale update"
    (let [http-client (HttpClientMock.)
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

      (given @(-> (fhir-client/update "http://localhost:8080/fhir" resource
                                      {:http-client http-client})
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/conflict
        [:fhir/issues 0 :severity] := #fhir/code"error"))))


(deftest execute-type-get-test
  (testing "success"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/ValueSet/$expand?url=http://hl7.org/fhir/ValueSet/administrative-gender")
          (.doReturn (j/write-value-as-string {:resourceType "ValueSet" :id "0"}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/execute-type-get
                "http://localhost:8080/fhir" "ValueSet" "expand"
                {:http-client http-client
                 :query-params
                 {:url "http://hl7.org/fhir/ValueSet/administrative-gender"}})
        :fhir/type := :fhir/ValueSet
        :id := "0"))))


(deftest search-type-test
  (testing "one bundle with one patient"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturn
            (j/write-value-as-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-type "http://localhost:8080/fhir" "Patient"
                                       {:http-client http-client})
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0")))

  (testing "one bundle with two patients"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturn
            (j/write-value-as-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}
                {:resource {:resourceType "Patient" :id "1"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-type "http://localhost:8080/fhir" "Patient"
                                       {:http-client http-client})
        count := 2
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1")))

  (testing "two bundles with two patients"
    (let [http-client (HttpClientMock.)]

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
                                       {:http-client http-client})
        count := 2
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1")))

  (testing "with query params"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient?birthdate=2020")
          (.doReturn
            (j/write-value-as-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-type "http://localhost:8080/fhir" "Patient"
                                       {:http-client http-client
                                        :query-params {:birthdate "2020"}})
        count := 1
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0")))

  (testing "Server Error without JSON response"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnStatus 500))

      (given @(-> (fhir-client/search-type "http://localhost:8080/fhir" "Patient"
                                           {:http-client http-client})
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/fault))))


(deftest search-system-test
  (testing "one bundle with one patient"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir")
          (.doReturn
            (j/write-value-as-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-system "http://localhost:8080/fhir"
                                         {:http-client http-client})
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        1 := nil)))

  (testing "one bundle with two patients"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir")
          (.doReturn
            (j/write-value-as-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}
                {:resource {:resourceType "Patient" :id "1"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-system "http://localhost:8080/fhir"
                                         {:http-client http-client})
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1"
        2 := nil)))

  (testing "two bundles with two patients"
    (let [http-client (HttpClientMock.)]

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
                                         {:http-client http-client})
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1"
        2 := nil)))

  (testing "with query params"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir?_id=0")
          (.doReturn
            (j/write-value-as-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (given @(fhir-client/search-system "http://localhost:8080/fhir"
                                         {:http-client http-client
                                          :query-params {"_id" "0"}})
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        1 := nil)))

  (testing "Server Error without JSON response"
    (let [http-client (HttpClientMock.)]

      (-> (.onGet http-client "http://localhost:8080/fhir")
          (.doReturnStatus 500))

      (given @(-> (fhir-client/search-system "http://localhost:8080/fhir"
                                             {:http-client http-client})
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/fault))))


(def temp-dir (Files/createTempDirectory "blaze" (make-array FileAttribute 0)))


(deftest spit-test
  (testing "success"
    (let [http-client (HttpClientMock.)
          publisher (fhir-client/search-type-publisher
                      "http://localhost:8080/fhir" "Patient"
                      {:http-client http-client})
          processor (fhir-client/resource-processor)
          future (fhir-client/spit temp-dir processor)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturn
            (j/write-value-as-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]}))
          (.withHeader "content-type" "application/fhir+json"))

      (.subscribe publisher processor)

      (is (= ["Patient-0.json"] (map #(str (.getFileName ^Path %)) @future)))))

  (testing "Server Error without JSON response"
    (let [http-client (HttpClientMock.)
          publisher (fhir-client/search-type-publisher
                      "http://localhost:8080/fhir" "Patient"
                      {:http-client http-client})
          processor (fhir-client/resource-processor)
          future (fhir-client/spit temp-dir processor)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnStatus 500))

      (.subscribe publisher processor)

      (given @(ac/exceptionally future (comp ex-data ex-cause))
        ::anom/category := ::anom/fault))))
