(ns blaze.interaction.transaction.bundle-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.fhir.spec.type :as type]
   [blaze.interaction.transaction.bundle :as bundle]
   [blaze.interaction.transaction.bundle-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest tx-ops-test
  (with-system [{:blaze.db/keys [node]} mem-node-config]
    (testing "create"
      (given (bundle/assoc-tx-ops
              (d/db node)
              [{:fhir/type :fhir.Bundle/entry
                :resource
                {:fhir/type :fhir/Patient :id "id-220129"
                 :meta (type/meta {:versionId #fhir/id "1"
                                   :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "Patient"}}])
        count := 1
        [0 :tx-op] := [:create {:fhir/type :fhir/Patient :id "id-220129"}]))

    (testing "conditional create"
      (given (bundle/assoc-tx-ops
              (d/db node)
              [{:fhir/type :fhir.Bundle/entry
                :resource
                {:fhir/type :fhir/Patient :id "id-220200"
                 :meta (type/meta {:versionId #fhir/id "1"
                                   :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "POST"
                 :url #fhir/uri "Patient"
                 :ifNoneExist #fhir/string "birthdate=2020"}}])
        count := 1
        [0 :tx-op] := [:create
                       {:fhir/type :fhir/Patient :id "id-220200"}
                       [["birthdate" "2020"]]])

      (testing "with empty :ifNoneExist"
        (given (bundle/assoc-tx-ops
                (d/db node)
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient :id "id-220200"
                   :meta (type/meta {:versionId #fhir/id "1"
                                     :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "POST"
                   :url #fhir/uri "Patient"
                   :ifNoneExist #fhir/string ""}}])
          count := 1
          [0 :tx-op] := [:create {:fhir/type :fhir/Patient :id "id-220200"}]))

      (testing "with ignorable _sort search parameter"
        (given (bundle/assoc-tx-ops
                (d/db node)
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient :id "id-220200"
                   :meta (type/meta {:versionId #fhir/id "1"
                                     :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "POST"
                   :url #fhir/uri "Patient"
                   :ifNoneExist #fhir/string "_sort=a"}}])
          count := 1
          [0 :tx-op] := [:create {:fhir/type :fhir/Patient :id "id-220200"}])))

    (testing "update"
      (given (bundle/assoc-tx-ops
              (d/db node)
              [{:fhir/type :fhir.Bundle/entry
                :resource
                {:fhir/type :fhir/Patient :id "id-214728"
                 :meta (type/meta {:versionId #fhir/id "1"
                                   :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "PUT"
                 :url #fhir/uri "Patient/id-214728"}}])
        count := 1
        [0 :tx-op] := [:put {:fhir/type :fhir/Patient :id "id-214728"}])

      (testing "with precondition"
        (given (bundle/assoc-tx-ops
                (d/db node)
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient :id "id-214728"
                   :meta (type/meta {:versionId #fhir/id "1"
                                     :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "PUT"
                   :url #fhir/uri "Patient/id-214728"
                   :ifMatch #fhir/string "W/\"215150\""}}])
          count := 1
          [0 :tx-op] := [:put
                         {:fhir/type :fhir/Patient :id "id-214728"}
                         [:if-match 215150]])))

    (testing "conditional update"
      (given (bundle/assoc-tx-ops
              (d/db node)
              [{:fhir/type :fhir.Bundle/entry
                :resource
                {:fhir/type :fhir/Patient :id "id-214728"
                 :meta (type/meta {:versionId #fhir/id "1"
                                   :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})}
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "PUT"
                 :url #fhir/uri "Patient/id-214728"
                 :ifNoneMatch #fhir/string "*"}}])
        count := 1
        [0 :tx-op] := [:put
                       {:fhir/type :fhir/Patient :id "id-214728"}
                       [:if-none-match :any]]))

    (testing "delete"
      (given (bundle/assoc-tx-ops
              (d/db node)
              [{:fhir/type :fhir.Bundle/entry
                :request
                {:fhir/type :fhir.Bundle.entry/request
                 :method #fhir/code "DELETE"
                 :url #fhir/uri "Patient/id-215232"}}])
        count := 1
        [0 :tx-op] := [:delete "Patient" "id-215232"]))

    (testing "conditional delete"
      (testing "without search params"
        (doseq [url [#fhir/uri "Patient" #fhir/uri "Patient?"]]
          (given (bundle/assoc-tx-ops
                  (d/db node)
                  [{:fhir/type :fhir.Bundle/entry
                    :request
                    {:fhir/type :fhir.Bundle.entry/request
                     :method #fhir/code "DELETE"
                     :url url}}])
            count := 1
            [0 :tx-op] := [:conditional-delete "Patient"])))

      (testing "with search params"
        (given (bundle/assoc-tx-ops
                (d/db node)
                [{:fhir/type :fhir.Bundle/entry
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "DELETE"
                   :url #fhir/uri "Patient?name-170043=value-170047"}}])
          count := 1
          [0 :tx-op] := [:conditional-delete "Patient" [["name-170043" "value-170047"]]]))))

  (with-system-data [{:blaze.db/keys [node]} mem-node-config]
    [[[:create {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "female"}]]
     [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]]]

    (testing "update"
      (testing "with older precondition"
        (given (bundle/assoc-tx-ops
                (d/db node)
                [{:fhir/type :fhir.Bundle/entry
                  :resource
                  {:fhir/type :fhir/Patient :id "0"
                   :meta (type/meta {:versionId #fhir/id "1"
                                     :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})
                   :gender #fhir/code "male"}
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "PUT"
                   :url #fhir/uri "Patient/0"
                   :ifMatch #fhir/string "W/\"1\""}}])
          ::anom/category := ::anom/conflict
          ::anom/message := "Precondition `W/\"1\"` failed on `Patient/0`."
          :http/status := 412)))))
