(ns blaze.interaction.transaction.bundle-test
  (:require
   [blaze.anomaly :as ba]
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-db with-system-data]]
   [blaze.fhir.spec.type :as type]
   [blaze.handler.fhir.util :as fhir-util]
   [blaze.interaction.transaction.bundle :as bundle]
   [blaze.interaction.transaction.bundle-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [given-failed-future]]
   [blaze.value-set-expander.protocols :as vsep]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(def ^:private value-set-expander
  (reify vsep/ValueSetExpander
    (-expand-condition-clauses [_ _ clauses]
      (ac/completed-future
       (condp = clauses
         [["gender:in" "url-1"]] [["gender" "system-1|male"]]
         [["gender:in" "url-empty"]] (ba/incorrect "msg-165412" :http/status 422)
         [["gender:in" "url-unknown"]] (ba/fault "msg-112933")
         clauses)))))

(defn- assoc-tx-ops [db entries]
  (bundle/assoc-tx-ops db @(bundle/expand-value-sets value-set-expander entries)))

(defn- conditional-create-entry [if-none-exist]
  {:fhir/type :fhir.Bundle/entry
   ::fhir-util/interaction "create"
   :resource {:fhir/type :fhir/Patient :id "id-112516"}
   :request
   {:fhir/type :fhir.Bundle.entry/request
    :method #fhir/code "POST"
    :url #fhir/uri "Patient"
    :ifNoneExist (type/string if-none-exist)}})

(defn- conditional-delete-entry [url]
  {:fhir/type :fhir.Bundle/entry
   ::fhir-util/interaction "conditional-delete-type"
   :request
   {:fhir/type :fhir.Bundle.entry/request
    :method #fhir/code "DELETE"
    :url (type/uri url)}})

(deftest expand-value-sets-test
  (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
    (with-db [db node]
      (testing "conditional create"
        (given (assoc-tx-ops db [(conditional-create-entry "gender:in=url-1")])
          [0 :tx-op] := [:create {:fhir/type :fhir/Patient :id "id-112516"}
                         [["gender" "system-1|male"]]])

        (testing "with an empty value set fails"
          (given-failed-future (bundle/expand-value-sets
                                value-set-expander
                                [(conditional-create-entry "gender:in=url-empty")])
            ::anom/category := ::anom/incorrect
            ::anom/message := "msg-165412"
            :http/status := 422)))

      (testing "conditional delete"
        (given (assoc-tx-ops db [(conditional-delete-entry "Patient?gender:in=url-1")])
          [0 :tx-op] := [:conditional-delete "Patient" [["gender" "system-1|male"]]])

        (testing "with an empty value set fails"
          (given-failed-future (bundle/expand-value-sets
                                value-set-expander
                                [(conditional-delete-entry "Patient?gender:in=url-empty")])
            ::anom/category := ::anom/incorrect
            ::anom/message := "msg-165412"
            :http/status := 422)))))

  (testing "expansion errors"
    (given-failed-future (bundle/expand-value-sets
                          value-set-expander
                          [(conditional-create-entry "gender:in=url-unknown")])
      ::anom/category := ::anom/fault
      ::anom/message := "msg-112933")))

(deftest tx-ops-test
  (with-system [{:blaze.db/keys [node]} api-stub/mem-node-config]
    (with-db [db node]
      (testing "create"
        (given (assoc-tx-ops
                db
                [{:fhir/type :fhir.Bundle/entry
                  ::fhir-util/interaction "create"
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
        (given (assoc-tx-ops
                db
                [{:fhir/type :fhir.Bundle/entry
                  ::fhir-util/interaction "create"
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
          (given (assoc-tx-ops
                  db
                  [{:fhir/type :fhir.Bundle/entry
                    ::fhir-util/interaction "create"
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
          (given (assoc-tx-ops
                  db
                  [{:fhir/type :fhir.Bundle/entry
                    ::fhir-util/interaction "create"
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
            [0 :tx-op] := [:create {:fhir/type :fhir/Patient :id "id-220200"}]))

        (testing "with search parameter without value"
          (given (assoc-tx-ops
                  db
                  [{:fhir/type :fhir.Bundle/entry
                    ::fhir-util/interaction "create"
                    :resource
                    {:fhir/type :fhir/Patient :id "id-220200"
                     :meta (type/meta {:versionId #fhir/id "1"
                                       :lastUpdated #fhir/instant #system/date-time "1970-01-01T00:00:00Z"})}
                    :request
                    {:fhir/type :fhir.Bundle.entry/request
                     :method #fhir/code "POST"
                     :url #fhir/uri "Patient"
                     :ifNoneExist #fhir/string "identifier"}}])
            count := 1
            [0 :tx-op] := [:create {:fhir/type :fhir/Patient :id "id-220200"}])))

      (testing "update"
        (given (assoc-tx-ops
                db
                [{:fhir/type :fhir.Bundle/entry
                  ::fhir-util/interaction "update"
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
          (given (assoc-tx-ops
                  db
                  [{:fhir/type :fhir.Bundle/entry
                    ::fhir-util/interaction "update"
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
        (given (assoc-tx-ops
                db
                [{:fhir/type :fhir.Bundle/entry
                  ::fhir-util/interaction "update"
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
        (given (assoc-tx-ops
                db
                [{:fhir/type :fhir.Bundle/entry
                  ::fhir-util/interaction "delete"
                  ::fhir-util/type "Patient"
                  ::fhir-util/id "id-215232"
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "DELETE"
                   :url #fhir/uri "Patient/id-215232"}}])
          count := 1
          [0 :tx-op] := [:delete "Patient" "id-215232"]))

      (testing "delete history version"
        (given (assoc-tx-ops
                db
                [{:fhir/type :fhir.Bundle/entry
                  ::fhir-util/interaction "delete-history-version"
                  ::fhir-util/type "Patient"
                  ::fhir-util/id "id-215232"
                  ::fhir-util/vid "1"
                  :request
                  {:fhir/type :fhir.Bundle.entry/request
                   :method #fhir/code "DELETE"
                   :url #fhir/uri "Patient/id-215232/_history/1"}}])
          count := 1
          [0 :tx-op] := [:delete-history-version "Patient" "id-215232" 1])

        (testing "with a version id that can't denote a version"
          (given (assoc-tx-ops
                  db
                  [{:fhir/type :fhir.Bundle/entry
                    ::fhir-util/interaction "delete-history-version"
                    ::fhir-util/type "Patient"
                    ::fhir-util/id "id-215232"
                    ::fhir-util/vid "a"
                    :request
                    {:fhir/type :fhir.Bundle.entry/request
                     :method #fhir/code "DELETE"
                     :url #fhir/uri "Patient/id-215232/_history/a"}}])
            count := 1
            [0 :tx-op] := nil)))

      (testing "conditional delete"
        (testing "without search params"
          (doseq [url [#fhir/uri "Patient" #fhir/uri "Patient?"]]
            (given (assoc-tx-ops
                    db
                    [{:fhir/type :fhir.Bundle/entry
                      ::fhir-util/interaction "conditional-delete-type"
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "DELETE"
                       :url url}}])
              count := 1
              [0 :tx-op] := [:conditional-delete "Patient"])))

        (testing "with search params"
          (given (assoc-tx-ops
                  db
                  [{:fhir/type :fhir.Bundle/entry
                    ::fhir-util/interaction "conditional-delete-type"
                    :request
                    {:fhir/type :fhir.Bundle.entry/request
                     :method #fhir/code "DELETE"
                     :url #fhir/uri "Patient?name-170043=value-170047"}}])
            count := 1
            [0 :tx-op] := [:conditional-delete "Patient" [["name-170043" "value-170047"]]]))

        (testing "with search params which are ignored"
          (doseq [url [#fhir/uri "Patient?name-170043" #fhir/uri "Patient?_sort=a"]]
            (given (assoc-tx-ops
                    db
                    [{:fhir/type :fhir.Bundle/entry
                      ::fhir-util/interaction "conditional-delete-type"
                      :request
                      {:fhir/type :fhir.Bundle.entry/request
                       :method #fhir/code "DELETE"
                       :url url}}])
              count := 1
              [0 :tx-op] := [:conditional-delete "Patient"]))))))

  (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
    [[[:create {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "female"}]]
     [[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}]]]

    (with-db [db node]
      (testing "update"
        (testing "with older precondition"
          (given (assoc-tx-ops
                  db
                  [{:fhir/type :fhir.Bundle/entry
                    ::fhir-util/interaction "update"
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
            :http/status := 412))))))
