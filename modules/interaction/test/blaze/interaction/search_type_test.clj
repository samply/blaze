(ns blaze.interaction.search-type-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.search-type :refer [handler]]
    [blaze.interaction.search-type-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log]))


(defn fixture [f]
  (st/instrument)
  (log/set-level! :trace)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def ^:private router
  (reitit/router
    [["/Patient/{id}" {:name :Patient/instance}]
     ["/MeasureReport/{id}" {:name :MeasureReport/instance}]
     ["/Library/{id}" {:name :Library/instance}]
     ["/List/{id}" {:name :List/instance}]]
    {:syntax :bracket}))


(def ^:private patient-match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.resource/type "Patient"}
   :path "/Patient"})


(def ^:private measure-report-match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.resource/type "MeasureReport"}
   :path "/MeasureReport"})


(def ^:private list-report-match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""
    :fhir.resource/type "List"}
   :path "/List"})


(defn- handler-with [txs]
  (fn [request]
    (with-open [node (mem-node-with txs)]
      @((handler node) request))))


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


(deftest handler-test
  (testing "Returns all existing resources of type"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Patient" :id "0"}]]])
           {::reitit/router router
            ::reitit/match patient-match})]

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
        (is (= "/Patient/0" (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (given (-> body :entry first :resource)
          :resourceType := "Patient"
          :id := "0"
          [:meta :versionId] := "1"
          [:meta :lastUpdated] := "1970-01-01T00:00:00Z"))))

  (testing "with param _summary equal to count"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Patient" :id "0"}]]])
           {::reitit/router router
            ::reitit/match patient-match
            :params {"_summary" "count"}})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= "Bundle" (:resourceType body))))

      (testing "the bundle type is searchset"
        (is (= "searchset" (:type body))))

      (testing "the total count is 1"
        (is (= 1 (:total body))))

      (testing "the bundle contains no entries"
        (is (empty? (:entry body))))))

  (testing "with param _count equal to zero"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:resourceType "Patient" :id "0"}]]])
           {::reitit/router router
            ::reitit/match patient-match
            :params {"_count" "0"}})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= "Bundle" (:resourceType body))))

      (testing "the bundle type is searchset"
        (is (= "searchset" (:type body))))

      (testing "the total count is 1"
        (is (= 1 (:total body))))

      (testing "the bundle contains no entries"
        (is (empty? (:entry body))))))

  (testing "with two patients"
    (with-open [node (mem-node-with
                       [[[:put {:resourceType "Patient" :id "0"}]
                         [:put {:resourceType "Patient" :id "1"}]]])]

      (testing "search for all patients with _count=1"
        (let [{:keys [body]}
              @((handler node)
                {::reitit/router router
                 ::reitit/match patient-match
                 :params {"_count" "1"}})]

          (testing "the total count is 2"
            (is (= 2 (:total body))))

          (testing "has a self link"
            (is (= "/Patient?_count=1&__t=1&__page-id=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= "/Patient?_count=1&__t=1&__page-id=1"
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @((handler node)
                {::reitit/router router
                 ::reitit/match patient-match
                 :params {"_count" "1" "__t" "1" "__page-id" "0"}})]

          (testing "the total count is 2"
            (is (= 2 (:total body))))

          (testing "has a self link"
            (is (= "/Patient?_count=1&__t=1&__page-id=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= "/Patient?_count=1&__t=1&__page-id=1" (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @((handler node)
                {::reitit/router router
                 ::reitit/match patient-match
                 :params {"_count" "1" "__t" "1" "__page-id" "1"}})]

          (testing "the total count is 2"
            (is (= 2 (:total body))))

          (testing "has a self link"
            (is (= "/Patient?_count=1&__t=1&__page-id=1"
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))))

  (testing "with three patients"
    (with-open [node (mem-node-with
                       [[[:put {:resourceType "Patient" :id "0"}]
                         [:put {:resourceType "Patient" :id "1" :active true}]
                         [:put {:resourceType "Patient" :id "2" :active true}]]])]

      (testing "search for active patients with _summary=count"
        (let [{:keys [body]}
              @((handler node)
                {::reitit/router router
                 ::reitit/match patient-match
                 :params {"active" "true" "_summary" "count"}})]

          (testing "their is a total count because we used _summary=count"
            (is (= 2 (:total body))))))

      (testing "search for active patients with _count=1"
        (let [{:keys [body]}
              @((handler node)
                {::reitit/router router
                 ::reitit/match patient-match
                 :params {"active" "true" "_count" "1"}})]

          (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
            (is (nil? (:total body))))

          (testing "has a self link"
            (is (= "/Patient?active=true&_count=1&__t=1&__page-offset=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= "/Patient?active=true&_count=1&__t=1&__page-offset=1" (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @((handler node)
                {::reitit/router router
                 ::reitit/match patient-match
                 :params {"active" "true" "_count" "1" "__t" "1" "__page-offset" "0"}})]

          (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
            (is (nil? (:total body))))

          (testing "has a self link"
            (is (= "/Patient?active=true&_count=1&__t=1&__page-offset=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= "/Patient?active=true&_count=1&__t=1&__page-offset=1"
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @((handler node)
                {::reitit/router router
                 ::reitit/match patient-match
                 :params {"active" "true" "_count" "1" "__t" "1" "__page-offset" "1"}})]

          (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
            (is (nil? (:total body))))

          (testing "has a self link"
            (is (= "/Patient?active=true&_count=1&__t=1&__page-offset=1"
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))))


  (testing "Id search"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "Patient" :id "0"}]
               [:put {:resourceType "Patient" :id "1"}]]])
           {::reitit/router router
            ::reitit/match {:data {:fhir.resource/type "Patient"}}
            :params {"_id" "0"}})]

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
        (is (= "/Patient/0" (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (given (-> body :entry first :resource)
          :resourceType := "Patient"
          :id := "0"))))

  (testing "Multiple Id search"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "Patient" :id "0"}]
               [:put {:resourceType "Patient" :id "1"}]
               [:put {:resourceType "Patient" :id "2"}]]])
           {::reitit/router router
            ::reitit/match {:data {:fhir.resource/type "Patient"}}
            :params {"_id" "0,2"}})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= "Bundle" (:resourceType body))))

      (testing "the bundle type is searchset"
        (is (= "searchset" (:type body))))

      (testing "the total count is 2"
        (is (= 2 (:total body))))

      (testing "the bundle contains one entry"
        (is (= 2 (count (:entry body)))))

      (testing "the first entry has the right fullUrl"
        (is (= "/Patient/0" (-> body :entry first :fullUrl))))

      (testing "the second entry has the right fullUrl"
        (is (= "/Patient/2" (-> body :entry second :fullUrl))))

      (testing "the first entry has the right resource"
        (given (-> body :entry first :resource)
          :resourceType := "Patient"
          :id := "0"))

      (testing "the second entry has the right resource"
        (given (-> body :entry second :resource)
          :resourceType := "Patient"
          :id := "2"))))

  (testing "_list search"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "Patient" :id "0"}]
               [:put {:resourceType "Patient" :id "1"}]
               [:put {:resourceType "List" :id "0"
                      :entry [{:item {:reference "Patient/0"}}]}]]])
           {::reitit/router router
            ::reitit/match {:data {:fhir.resource/type "Patient"}}
            :params {"_list" "0"}})]

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
        (is (= "/Patient/0" (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (given (-> body :entry first :resource)
          :resourceType := "Patient"
          :id := "0"))))

  (testing "Patient identifier search"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "Patient" :id "0"
                      :identifier [{:value "0"}]}]
               [:put {:resourceType "Patient" :id "1"
                      :identifier [{:value "1"}]}]]])
           {::reitit/router router
            ::reitit/match patient-match
            :params {"identifier" "0"}})]

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
        (is (= "/Patient/0" (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (given (-> body :entry first :resource)
          [:identifier 0 :value] := "0"))))

  (testing "Patient language search"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "Patient" :id "0"
                      :communication
                      [{:language
                        {:coding
                         [{:system "urn:ietf:bcp:47"
                           :code "de"}]}}
                       {:language
                        {:coding
                         [{:system "urn:ietf:bcp:47"
                           :code "en"}]}}]}]
               [:put {:resourceType "Patient" :id "1"
                      :communication
                      [{:language
                        {:coding
                         [{:system "urn:ietf:bcp:47"
                           :code "de"}]}}]}]]])
           {::reitit/router router
            ::reitit/match patient-match
            :params {"language" ["de" "en"]}})]

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
        (is (= "/Patient/0" (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (is (= "0" (-> body :entry first :resource :id))))))

  (testing "Library title search"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "Library" :id "0" :title "ab"}]
               [:put {:resourceType "Library" :id "1" :title "b"}]]])
           {::reitit/router router
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
        (is (= "/Library/0" (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (given (-> body :entry first :resource)
          :resourceType := "Library"
          :id := "0"))))

  #_(testing "Library title:contains search"
      (let [{:keys [status body]}
            ((handler-with
               [[[:put {:resourceType "Library" :id "0" :title "bab"}]
                 [:put {:resourceType "Library" :id "1" :title "b"}]]])
             {::reitit/router router
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
          (is (= "/Library/0" (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :resourceType := "Library"
            :id := "0"))))

  (testing "MeasureReport measure search"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "MeasureReport" :id "0"
                      :measure "http://server.com/Measure/0"}]]
              [[:put {:resourceType "MeasureReport" :id "1"
                      :measure "http://server.com/Measure/1"}]]])
           {::reitit/router router
            ::reitit/match measure-report-match
            :params {"measure" "http://server.com/Measure/0"}})]

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
        (is (= "/MeasureReport/0" (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (given (-> body :entry first :resource)
          :measure := "http://server.com/Measure/0"))))

  #_(testing "Measure title sort asc"
      (let [measure-1 {:resourceType "Measure" "id" "1"}
            measure-2 {:resourceType "Measure" "id" "2"}]
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
              @((handler ::node)
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

  #_(testing "Measure title sort desc"
      (let [measure-1 {:resourceType "Measure" "id" "1"}
            measure-2 {:resourceType "Measure" "id" "2"}]
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
              @((handler ::node)
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
            (is (= measure-1 (-> body :entry second :resource)))))))

  (testing "List item search"
    (let [{:keys [status body]}
          ((handler-with
             [[[:put {:resourceType "List"
                      :id "id-123058"
                      :entry
                      [{:item
                        {:identifier
                         {:system "system-122917"
                          :value "value-122931"}}}]}]
               [:put {:resourceType "List"
                      :id "id-143814"
                      :entry
                      [{:item
                        {:identifier
                         {:system "system-122917"
                          :value "value-143818"}}}]}]]])
           {::reitit/router router
            ::reitit/match list-report-match
            :params {"item:identifier" "system-122917|value-143818"}})]

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
        (is (= "/List/id-143814" (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (given (-> body :entry first :resource)
          :id := "id-143814")))))
