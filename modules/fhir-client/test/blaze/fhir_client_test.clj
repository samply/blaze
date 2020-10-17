(ns blaze.fhir-client-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.fhir-client :as fhir-client]
    [blaze.fhir-client-spec]
    [blaze.fhir.spec :as fhir-spec]
    [cheshire.core :as json]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cognitect.anomalies :as anom]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [com.pgssoft.httpclient HttpClientMock]
    [org.hamcrest.core Is]
    [java.nio.file Files Path]
    [java.nio.file.attribute FileAttribute]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest client-test
  (is (fhir-client/client "http://localhost:8080/fhir")))


(deftest metadata-test
  (let [http-client (HttpClientMock.)
        client (fhir-client/client http-client "http://localhost:8080/fhir")]

    (-> (.onGet http-client "http://localhost:8080/fhir/metadata")
        (.doReturnJSON (json/generate-string {:resourceType "CapabilityStatement"})))

    (given @(fhir-client/metadata client)
      :fhir/type := :fhir/CapabilityStatement)))


(deftest read-test
  (testing "success"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn (json/generate-string {:resourceType "Patient" :id "0"}))
          (.withHeader "Content-Type" "application/fhir+json"))

      (given @(fhir-client/read client "Patient" "0")
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "with application/fhir+json Content-Type"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturnJSON (json/generate-string {:resourceType "Patient" :id "0"})))

      (given @(fhir-client/read client "Patient" "0")
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "not-found"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn
            404
            (json/generate-string
              {:resourceType "OperationOutcome"
               :issue
               [{:severity "error"
                 :code "not-found"}]}))
          (.withHeader "Content-Type" "application/json"))

      (given @(-> (fhir-client/read client "Patient" "0")
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/not-found
        [:fhir/issues 0 :severity] := #fhir/code"error"
        [:fhir/issues 0 :code] := #fhir/code"not-found")))

  (testing "Server Error without JSON response"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturnStatus 500))

      (given @(-> (fhir-client/read client "Patient" "0")
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/fault)))

  (testing "Service Unavailable without JSON response"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn 503 "Service Unavailable"))

      (given @(-> (fhir-client/read client "Patient" "0")
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/unavailable)))

  (testing "Gateway timeout without JSON response (external load-balancer)"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient/0")
          (.doReturn 504 "Gateway Timeout"))

      (given @(-> (fhir-client/read client "Patient" "0")
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/busy))))


(deftest update-test
  (testing "without meta versionId"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")
          resource {:fhir/type :fhir/Patient :id "0"}]

      (-> (.onPut http-client "http://localhost:8080/fhir/Patient/0")
          (.withBody
            (Is/is (json/generate-string (fhir-spec/unform-json resource))))
          (.doReturnJSON
            (json/generate-string {:resourceType "Patient" :id "0"})))

      (given @(fhir-client/update client resource)
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "with meta versionId"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")
          resource {:fhir/type :fhir/Patient :id "0"
                    :meta
                    {:fhir/type :fhir/Meta
                     :versionId #fhir/id"180040"}}]

      (-> (.onPut http-client "http://localhost:8080/fhir/Patient/0")
          (.withHeader "If-Match" "W/\"180040\"")
          (.withBody
            (Is/is (json/generate-string (fhir-spec/unform-json resource))))
          (.doReturnJSON
            (json/generate-string {:resourceType "Patient" :id "0"})))

      (given @(fhir-client/update client resource)
        :fhir/type := :fhir/Patient
        :id := "0")))

  (testing "stale update"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")
          resource {:fhir/type :fhir/Patient :id "0"
                    :meta
                    {:fhir/type :fhir/Meta
                     :versionId #fhir/id"180040"}}]

      (-> (.onPut http-client "http://localhost:8080/fhir/Patient/0")
          (.withHeader "If-Match" "W/\"180040\"")
          (.withBody
            (Is/is (json/generate-string (fhir-spec/unform-json resource))))

          (.doReturn
            412
            (json/generate-string
              {:resourceType "OperationOutcome"
               :issue
               [{:severity "error"}]}))
          (.withHeader "Content-Type" "application/json"))

      (given @(-> (fhir-client/update client resource)
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/conflict
        [:fhir/issues 0 :severity] := #fhir/code"error"))))


(deftest search-type-test
  (testing "one bundle with one patient"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnJSON
            (json/generate-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]})))

      (given @(fhir-client/search-type client "Patient")
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        1 := nil)))

  (testing "one bundle with two patients"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnJSON
            (json/generate-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}
                {:resource {:resourceType "Patient" :id "1"}}]})))

      (given @(fhir-client/search-type client "Patient")
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1"
        2 := nil)))

  (testing "two bundles with two patients"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnJSON
            (json/generate-string
              {:resourceType "Bundle"
               :link
               [{:relation "next"
                 :url "http://localhost:8080/fhir/Patient?page=2"}]
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]})))

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient?page=2")
          (.doReturnJSON
            (json/generate-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "1"}}]})))

      (given @(fhir-client/search-type client "Patient")
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        [1 :fhir/type] := :fhir/Patient
        [1 :id] := "1"
        2 := nil)))

  (testing "with query params"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient?birthdate=2020")
          (.doReturnJSON
            (json/generate-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]})))

      (given @(fhir-client/search-type client "Patient" {"birthdate" "2020"})
        [0 :fhir/type] := :fhir/Patient
        [0 :id] := "0"
        1 := nil)))

  (testing "Server Error without JSON response"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient?birthdate=2020")
          (.doReturnStatus 500))

      (given @(-> (fhir-client/search-type client "Patient" {"birthdate" "2020"})
                  (ac/exceptionally (comp ex-data ex-cause)))
        ::anom/category := ::anom/fault))))


(def temp-dir (Files/createTempDirectory "blaze" (make-array FileAttribute 0)))


(deftest spit-test
  (testing "success"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")
          publisher (fhir-client/search-type-publisher client "Patient" {})
          processor (fhir-client/resource-processor)
          future (fhir-client/spit temp-dir processor)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnJSON
            (json/generate-string
              {:resourceType "Bundle"
               :entry
               [{:resource {:resourceType "Patient" :id "0"}}]})))

      (.subscribe publisher processor)

      (is (= ["Patient-0.json"] (map #(str (.getFileName ^Path %)) @future)))))

  (testing "Server Error without JSON response"
    (let [http-client (HttpClientMock.)
          client (fhir-client/client http-client "http://localhost:8080/fhir")
          publisher (fhir-client/search-type-publisher client "Patient" {})
          processor (fhir-client/resource-processor)
          future (fhir-client/spit temp-dir processor)]

      (-> (.onGet http-client "http://localhost:8080/fhir/Patient")
          (.doReturnStatus 500))

      (.subscribe publisher processor)

      (given @(ac/exceptionally future (comp ex-data ex-cause))
        ::anom/category := ::anom/fault))))


