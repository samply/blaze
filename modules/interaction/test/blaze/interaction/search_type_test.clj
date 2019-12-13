(ns blaze.interaction.search-type-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.datomic.test-util :as datomic-test-util]
    [blaze.interaction.search-type :refer [handler]]
    [blaze.interaction.test-util :as test-util]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [datomic.api :as d]
    [datomic-spec.test :as dst]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (dst/instrument)
  (st/instrument
    [`handler]
    {:spec
     {`handler
      (s/fspec
        :args (s/cat :conn #{::conn}))}})
  (datomic-test-util/stub-db ::conn ::db)
  (log/with-merged-config {:level :error} (f))
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn stub-entity-replace [db eid-spec replace-fn]
  (st/instrument
    [`d/entity]
    {:spec
     {`d/entity
      (s/fspec
        :args (s/cat :db #{db} :eid eid-spec))}
     :replace
     {`d/entity replace-fn}}))


(deftest handler-test
  (testing "Returns all existing resources of type"
    (let [patient {"resourceType" "Patient" "id" "0"}]
      (datomic-test-util/stub-list-resources ::db "Patient" #{[::patient]})
      (datomic-test-util/stub-pull-resource* ::db "Patient" ::patient #{patient})
      (datomic-test-util/stub-type-total ::db "Patient" 1)
      (test-util/stub-instance-url ::router "Patient" "0" ::patient-url)

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "Patient"}}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the total count is 1"
          (is (= 1 (:total body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= ::patient-url (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (is (= patient (-> body :entry first :resource)))))))

  (testing "With param _summary equal to count"
    (datomic-test-util/stub-type-total ::db "Patient" 42)

    (let [{:keys [status body]}
          @((handler ::conn)
            {::reitit/match {:data {:fhir.resource/type "Patient"}}
             :params {"_summary" "count"}})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= "Bundle" (:resourceType body))))

      (testing "the bundle type is searchset"
        (is (= "searchset" (:type body))))

      (testing "the total count is 42"
        (is (= 42 (:total body))))

      (testing "the bundle contains no entries"
        (is (empty? (:entry body))))))

  (testing "With param _count equal to zero"
    (datomic-test-util/stub-type-total ::db "Patient" 23)

    (let [{:keys [status body]}
          @((handler ::conn)
            {::reitit/match {:data {:fhir.resource/type "Patient"}}
             :params {"_count" "0"}})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= "Bundle" (:resourceType body))))

      (testing "the bundle type is searchset"
        (is (= "searchset" (:type body))))

      (testing "he total count is 23"
        (is (= 23 (:total body))))

      (testing "the bundle contains no entries"
        (is (empty? (:entry body))))))


  (testing "Id search"
    (let [patient-0 {:Patient/id "0"}
          patient-1 {:Patient/id "1"}
          pulled-patient-0 {"resourceType" "Patient" "id" "0"}]
      (datomic-test-util/stub-list-resources
        ::db "Patient" #{[patient-0 patient-1]})
      (datomic-test-util/stub-pull-resource*
        ::db "Patient" patient-0 #{pulled-patient-0})
      (test-util/stub-instance-url ::router "Patient" "0" ::patient-url)

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "Patient"}}
               :params {"_id" "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= ::patient-url (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (is (= pulled-patient-0 (-> body :entry first :resource)))))))

  (testing "Multiple Id search"
    (let [patient-0 {:Patient/id "0"}
          patient-1 {:Patient/id "1"}
          patient-2 {:Patient/id "2"}
          pulled-patient-0 {"resourceType" "Patient" "id" "0"}
          pulled-patient-2 {"resourceType" "Patient" "id" "2"}]
      (datomic-test-util/stub-list-resources
        ::db "Patient" #{[patient-0 patient-1 patient-2]})
      (datomic-test-util/stub-pull-resource*-fn
        ::db "Patient" #{patient-0 patient-2}
        (fn [_ _ resource]
          (condp = resource
            patient-0 pulled-patient-0
            patient-2 pulled-patient-2)))
      (test-util/stub-instance-url-fn
        ::router "Patient" #{"0" "2"}
        (fn [_ _ resource]
          (case resource
            "0" ::patient-url-0
            "2" ::patient-url-2)))

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "Patient"}}
               :params {"_id" "0,2"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the bundle contains one entry"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= ::patient-url-0 (-> body :entry first :fullUrl))))

        (testing "the second entry has the right fullUrl"
          (is (= ::patient-url-2 (-> body :entry second :fullUrl))))

        (testing "the first entry has the right resource"
          (is (= pulled-patient-0 (-> body :entry first :resource))))

        (testing "the second entry has the right resource"
          (is (= pulled-patient-2 (-> body :entry second :resource)))))))

  (testing "Identifier search"
    (let [patient-0 {:Patient/identifier [{:Identifier/value "0"}]}
          patient-1 {:Patient/identifier [{:Identifier/value "1"}]}
          pulled-patient-0 {"resourceType" "Patient" "id" "0"}]
      (datomic-test-util/stub-list-resources
        ::db "Patient" #{[patient-0 patient-1]})
      (datomic-test-util/stub-cached-entity
        ::db #{:Patient/identifier} #{{:db/cardinality :db.cardinality/many}})
      (datomic-test-util/stub-pull-resource*
        ::db "Patient" patient-0 #{pulled-patient-0})
      (test-util/stub-instance-url ::router "Patient" "0" ::patient-url)

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "Patient"}}
               :params {"identifier" "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= ::patient-url (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (is (= pulled-patient-0 (-> body :entry first :resource)))))))

  (testing "Library title search"
    (let [library-0 {:Library/title "ab"}
          library-1 {:Library/title "b"}
          pulled-library-0 {"resourceType" "Library" "id" "0"}]
      (datomic-test-util/stub-list-resources
        ::db "Library" #{[library-0 library-1]})
      (datomic-test-util/stub-cached-entity
        ::db #{:Library/title} #{{:db/cardinality :db.cardinality/one}})
      (datomic-test-util/stub-pull-resource*
        ::db "Library" library-0 #{pulled-library-0})
      (test-util/stub-instance-url ::router "Library" "0" ::library-url)

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "Library"}}
               :params {"title" "A"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= ::library-url (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (is (= pulled-library-0 (-> body :entry first :resource)))))))

  (testing "Library title:contains search"
    (let [library-0 {:Library/title "bab"}
          library-1 {:Library/title "b"}
          pulled-library-0 {"resourceType" "Library" "id" "0"}]
      (datomic-test-util/stub-list-resources
        ::db "Library" #{[library-0 library-1]})
      (datomic-test-util/stub-cached-entity
        ::db #{:Library/title} #{{:db/cardinality :db.cardinality/one}})
      (datomic-test-util/stub-pull-resource*
        ::db "Library" library-0 #{pulled-library-0})
      (test-util/stub-instance-url ::router "Library" "0" ::library-url)

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "Library"}}
               :params {"title:contains" "A"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= ::library-url (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (is (= pulled-library-0 (-> body :entry first :resource)))))))

  (testing "MeasureReport measure search"
    (let [report-0 {:MeasureReport/measure "http://server.com/Measure/0"}
          report-1 {:MeasureReport/measure "http://server.com/Measure/1"}
          pulled-report-0 {"resourceType" "MeasureReport" "id" "0"}]
      (datomic-test-util/stub-list-resources
        ::db "MeasureReport" #{[report-0 report-1]})
      (datomic-test-util/stub-cached-entity
        ::db #{:MeasureReport/measure} #{{:db/cardinality :db.cardinality/one}})
      (datomic-test-util/stub-pull-resource*
        ::db "MeasureReport" report-0 #{pulled-report-0})
      (test-util/stub-instance-url ::router "MeasureReport" "0" ::report-url)

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "MeasureReport"}}
               :params {"measure" "http://server.com/Measure/0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= ::report-url (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (is (= pulled-report-0 (-> body :entry first :resource)))))))

  (testing "Measure title sort asc"
    (let [measure-1 {"resourceType" "Measure" "id" "1"}
          measure-2 {"resourceType" "Measure" "id" "2"}]
      (datomic-test-util/stub-find-search-param-by-type-and-code
        ::db "Measure" "title" #{::search-param})
      (datomic-test-util/stub-list-resources-sorted-by
        ::db "Measure" ::search-param #{[::measure-1 ::measure-2]})
      (datomic-test-util/stub-pull-resource*-fn
        ::db "Measure" #{::measure-1 ::measure-2}
        (fn [_ _ r] (case r ::measure-1 measure-1 ::measure-2 measure-2)))
      (datomic-test-util/stub-type-total ::db "Measure" 2)
      (test-util/stub-instance-url-fn
        ::router "Measure" #{"1" "2"}
        (fn [_ _ id] (keyword (str "measure-url-" id))))

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "Measure"}}
               :params {"_sort" "title"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the total count is 2"
          (is (= 2 (:total body))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= :measure-url-1 (-> body :entry first :fullUrl))))

        (testing "the second entry has the right fullUrl"
          (is (= :measure-url-2 (-> body :entry second :fullUrl))))

        (testing "the first entry has the right resource"
          (is (= measure-1 (-> body :entry first :resource))))

        (testing "the second entry has the right resource"
          (is (= measure-2 (-> body :entry second :resource)))))))

  (testing "Measure title sort desc"
    (let [measure-1 {"resourceType" "Measure" "id" "1"}
          measure-2 {"resourceType" "Measure" "id" "2"}]
      (datomic-test-util/stub-find-search-param-by-type-and-code
        ::db "Measure" "title" #{::search-param})
      (datomic-test-util/stub-list-resources-sorted-by
        ::db "Measure" ::search-param #{[::measure-1 ::measure-2]})
      (datomic-test-util/stub-pull-resource*-fn
        ::db "Measure" #{::measure-1 ::measure-2}
        (fn [_ _ r] (case r ::measure-1 measure-1 ::measure-2 measure-2)))
      (datomic-test-util/stub-type-total ::db "Measure" 2)
      (test-util/stub-instance-url-fn
        ::router "Measure" #{"1" "2"}
        (fn [_ _ id] (keyword (str "measure-url-" id))))

      (let [{:keys [status body]}
            @((handler ::conn)
              {::reitit/router ::router
               ::reitit/match {:data {:fhir.resource/type "Measure"}}
               :params {"_sort" "-title"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= "Bundle" (:resourceType body))))

        (testing "the bundle type is searchset"
          (is (= "searchset" (:type body))))

        (testing "the total count is 2"
          (is (= 2 (:total body))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= :measure-url-2 (-> body :entry first :fullUrl))))

        (testing "the second entry has the right fullUrl"
          (is (= :measure-url-1 (-> body :entry second :fullUrl))))

        (testing "the first entry has the right resource"
          (is (= measure-2 (-> body :entry first :resource))))

        (testing "the second entry has the right resource"
          (is (= measure-1 (-> body :entry second :resource))))))))
