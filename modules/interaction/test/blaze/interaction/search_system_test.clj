(ns blaze.interaction.search-system-test
  "Specifications relevant for the FHIR search interaction:

  https://www.hl7.org/fhir/http.html#search"
  (:require
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.interaction.search-system]
    [blaze.interaction.search.nav-spec]
    [blaze.interaction.search.params-spec]
    [blaze.interaction.test-util :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Clock Instant ZoneId]
    [java.util Random]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))


(def ^:private base-url "base-url-114650")


(defn fixed-random [n]
  (proxy [Random] []
    (nextLong []
      n)))


(def ^:private router
  (reitit/router
    [["/Patient" {:name :Patient/type}]]
    {:syntax :bracket}))


(def ^:private match
  {:data
   {:blaze/base-url ""
    :blaze/context-path ""}
   :path ""})


(defn- handler [node]
  (-> (ig/init
        {:blaze.interaction/search-system
         {:node node
          :clock clock
          :rng-fn (fn [] (fixed-random 0))}})
      :blaze.interaction/search-system))


(defn- handler-with [txs & [:as opts]]
  (fn [request]
    (with-open [node (apply mem-node-with txs opts)]
      @((handler node)
        (assoc request
          :blaze/base-url base-url
          ::reitit/router router)))))


(defn- link-url [body link-relation]
  (->> body :link (filter (comp #{link-relation} :relation)) first :url))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/search-system nil})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/search-system {}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-thrown (ig/init {:blaze.interaction/search-system {:node "foo"}})
      :key := :blaze.interaction/search-system
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 2 :pred] := `blaze.db.spec/node?
      [:explain ::s/problems 2 :val] := "foo")))


(deftest handler-test
  #_(testing "on unknown search parameter"
      (testing "with strict handling"
        (testing "returns error"
          (testing "normal result"
            (let [{:keys [status body]}
                  ((handler-with [])
                   {::reitit/match match
                    :headers {"prefer" "handling=strict"}
                    :params {"foo" "bar"}})]

              (is (= 404 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"not-found"
                [:issue 0 :diagnostics] := "The search-param with code `foo` and type `Patient` was not found.")))

          (testing "summary result"
            (let [{:keys [status body]}
                  ((handler-with [])
                   {::reitit/match match
                    :headers {"prefer" "handling=strict"}
                    :params {"foo" "bar" "_summary" "count"}})]

              (is (= 404 status))

              (given body
                :fhir/type := :fhir/OperationOutcome
                [:issue 0 :severity] := #fhir/code"error"
                [:issue 0 :code] := #fhir/code"not-found"
                [:issue 0 :diagnostics] := "The search-param with code `foo` and type `Patient` was not found.")))))

      (testing "with lenient handling"
        (testing "returns results with a self link lacking the unknown search parameter"
          (testing "where the unknown search parameter is the only one"
            (testing "normal result"
              (let [{:keys [status body]}
                    ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
                     {::reitit/match match
                      :headers {"prefer" "handling=lenient"}
                      :params {"foo" "bar"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle contains an id"
                  (is (string? (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= #fhir/uri"?_count=50&__t=1&__page-id=0"
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
                     {::reitit/match match
                      :headers {"prefer" "handling=lenient"}
                      :params {"foo" "bar" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle contains an id"
                  (is (string? (:id body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entries"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= #fhir/uri"?_summary=count&_count=50&__t=1"
                         (link-url body "self")))))))

          (testing "with another search parameter"
            (testing "normal result"
              (let [{:keys [status body]}
                    ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]
                                     [:put {:fhir/type :fhir/Patient :id "1"
                                            :active true}]]])
                     {::reitit/match match
                      :headers {"prefer" "handling=lenient"}
                      :params {"foo" "bar" "active" "true"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains one entry"
                  (is (= 1 (count (:entry body)))))

                (testing "has a self link"
                  (is (= #fhir/uri"?active=true&_count=50&__t=1&__page-id=1"
                         (link-url body "self"))))))

            (testing "summary result"
              (let [{:keys [status body]}
                    ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]
                                     [:put {:fhir/type :fhir/Patient :id "1"
                                            :active true}]]])
                     {::reitit/match match
                      :headers {"prefer" "handling=lenient"}
                      :params {"foo" "bar" "active" "true" "_summary" "count"}})]

                (is (= 200 status))

                (testing "the body contains a bundle"
                  (is (= :fhir/Bundle (:fhir/type body))))

                (testing "the bundle type is searchset"
                  (is (= #fhir/code"searchset" (:type body))))

                (testing "the total count is 1"
                  (is (= #fhir/unsignedInt 1 (:total body))))

                (testing "the bundle contains no entries"
                  (is (empty? (:entry body))))

                (testing "has a self link"
                  (is (= #fhir/uri"?active=true&_summary=count&_count=50&__t=1"
                         (link-url body "self"))))))))))

  (testing "Returns all existing resources"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
           {::reitit/match match})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= :fhir/Bundle (:fhir/type body))))

      (testing "the bundle id is an LUID"
        (is (= "AAAAAAAAAAAAAAAA" (:id body))))

      (testing "the bundle type is searchset"
        (is (= #fhir/code"searchset" (:type body))))

      (testing "the total count is 1"
        (is (= #fhir/unsignedInt 1 (:total body))))

      (testing "has a self link"
        (is (= #fhir/uri"base-url-114650?_count=50&__t=1&__page-type=Patient&__page-id=0"
               (link-url body "self"))))

      (testing "the bundle contains one entry"
        (is (= 1 (count (:entry body)))))

      (testing "the entry has the right fullUrl"
        (is (= #fhir/uri"base-url-114650/Patient/0"
               (-> body :entry first :fullUrl))))

      (testing "the entry has the right resource"
        (given (-> body :entry first :resource)
          :fhir/type := :fhir/Patient
          :id := "0"
          [:meta :versionId] := #fhir/id"1"
          [:meta :lastUpdated] := Instant/EPOCH))))

  (testing "with param _summary equal to count"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
           {::reitit/match match
            :params {"_summary" "count"}})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= :fhir/Bundle (:fhir/type body))))

      (testing "the bundle id is an LUID"
        (is (= "AAAAAAAAAAAAAAAA" (:id body))))

      (testing "the bundle type is searchset"
        (is (= #fhir/code"searchset" (:type body))))

      (testing "the total count is 1"
        (is (= #fhir/unsignedInt 1 (:total body))))

      (testing "has a self link"
        (is (= #fhir/uri"base-url-114650?_summary=count&_count=50&__t=1"
               (link-url body "self"))))

      (testing "the bundle contains no entries"
        (is (empty? (:entry body))))))

  (testing "with param _count equal to zero"
    (let [{:keys [status body]}
          ((handler-with [[[:put {:fhir/type :fhir/Patient :id "0"}]]])
           {::reitit/match match
            :params {"_count" "0"}})]

      (is (= 200 status))

      (testing "the body contains a bundle"
        (is (= :fhir/Bundle (:fhir/type body))))

      (testing "the bundle type is searchset"
        (is (= #fhir/code"searchset" (:type body))))

      (testing "the total count is 1"
        (is (= #fhir/unsignedInt 1 (:total body))))

      (testing "has a self link"
        (is (= #fhir/uri"base-url-114650?_count=0&__t=1" (link-url body "self"))))

      (testing "the bundle contains no entries"
        (is (empty? (:entry body))))))

  (testing "with two patients"
    (with-open [node (mem-node-with
                       [[[:put {:fhir/type :fhir/Patient :id "0"}]
                         [:put {:fhir/type :fhir/Patient :id "1"}]]])]

      (testing "search for all patients with _count=1"
        (let [{:keys [body]}
              @((handler node)
                {:blaze/base-url base-url
                 ::reitit/router router
                 ::reitit/match match
                 :params {"_count" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=1"
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the self link"
        (let [{:keys [body]}
              @((handler node)
                {:blaze/base-url base-url
                 ::reitit/router router
                 ::reitit/match match
                 :params {"_count" "1" "__t" "1" "__page-type" "Patient"
                          "__page-id" "0"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=0"
                   (link-url body "self"))))

          (testing "has a next link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=1"
                   (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "following the next link"
        (let [{:keys [body]}
              @((handler node)
                {:blaze/base-url base-url
                 ::reitit/router router
                 ::reitit/match match
                 :params {"_count" "1" "__t" "1" "__page-type" "Patient"
                          "__page-id" "1"}})]

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "has a self link"
            (is (= #fhir/uri"base-url-114650?_count=1&__t=1&__page-type=Patient&__page-id=1"
                   (link-url body "self"))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))))

  #_(testing "with three patients"
      (with-open [node (mem-node-with
                         [[[:put {:fhir/type :fhir/Patient :id "0"}]
                           [:put {:fhir/type :fhir/Patient :id "1" :active true}]
                           [:put {:fhir/type :fhir/Patient :id "2" :active true}]]])]

        (testing "search for active patients with _summary=count"
          (let [{:keys [body]}
                @((handler node)
                  {::reitit/match match
                   :params {"active" "true" "_summary" "count"}})]

            (testing "their is a total count because we used _summary=count"
              (is (= #fhir/unsignedInt 2 (:total body))))))

        (testing "search for active patients with _count=1"
          (let [{:keys [body]}
                @((handler node)
                  {::reitit/match match
                   :params {"active" "true" "_count" "1"}})]

            (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= #fhir/uri"?active=true&_count=1&__t=1&__page-id=1"
                     (link-url body "self"))))

            (testing "has a next link"
              (is (= #fhir/uri"?active=true&_count=1&__t=1&__page-id=2"
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "following the self link"
          (let [{:keys [body]}
                @((handler node)
                  {::reitit/match match
                   :params {"active" "true" "_count" "1" "__t" "1" "__page-id" "1"}})]

            (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= #fhir/uri"?active=true&_count=1&__t=1&__page-id=1"
                     (link-url body "self"))))

            (testing "has a next link"
              (is (= #fhir/uri"?active=true&_count=1&__t=1&__page-id=2"
                     (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))

        (testing "following the next link"
          (let [{:keys [body]}
                @((handler node)
                  {::reitit/match match
                   :params {"active" "true" "_count" "1" "__t" "1" "__page-id" "2"}})]

            (testing "their is no total count because we have clauses and we have
                    more hits than page-size"
              (is (nil? (:total body))))

            (testing "has a self link"
              (is (= #fhir/uri"?active=true&_count=1&__t=1&__page-id=2"
                     (link-url body "self"))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))))))


  #_(testing "Id search"
      (let [{:keys [status body]}
            ((handler-with
               [[[:put {:fhir/type :fhir/Patient :id "0"}]
                 [:put {:fhir/type :fhir/Patient :id "1"}]]])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :params {"_id" "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= #fhir/uri"/Patient/0" (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Patient
            :id := "0"))))

  #_(testing "Multiple Id search"
      (let [{:keys [status body]}
            ((handler-with
               [[[:put {:fhir/type :fhir/Patient :id "0"}]
                 [:put {:fhir/type :fhir/Patient :id "1"}]
                 [:put {:fhir/type :fhir/Patient :id "2"}]]])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :params {"_id" "0,2"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "the bundle contains one entry"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= #fhir/uri"/Patient/0" (-> body :entry first :fullUrl))))

        (testing "the second entry has the right fullUrl"
          (is (= #fhir/uri"/Patient/2" (-> body :entry second :fullUrl))))

        (testing "the first entry has the right resource"
          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Patient
            :id := "0"))

        (testing "the second entry has the right resource"
          (given (-> body :entry second :resource)
            :fhir/type := :fhir/Patient
            :id := "2"))))

  #_(testing "_lastUpdated search"
      (testing "the resource is created at EPOCH"
        (let [{:keys [status body]}
              ((handler-with
                 [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
                 :clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))
               {::reitit/match match
                :params {"_lastUpdated" "1970-01-01"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 1"
            (is (= #fhir/unsignedInt 1 (:total body))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))))

      (testing "no resource is created after EPOCH"
        (let [{:keys [status body]}
              ((handler-with
                 [[[:put {:fhir/type :fhir/Patient :id "0"}]]]
                 :clock (Clock/fixed Instant/EPOCH (ZoneId/of "UTC")))
               {::reitit/match match
                :params {"_lastUpdated" "gt1970-01-02"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 0"
            (is (= #fhir/unsignedInt 0 (:total body))))

          (testing "the bundle contains no entry"
            (is (zero? (count (:entry body))))))))

  #_(testing "_list search"
      (let [{:keys [status body]}
            ((handler-with
               [[[:put {:fhir/type :fhir/Patient :id "0"}]
                 [:put {:fhir/type :fhir/Patient :id "1"}]
                 [:put {:fhir/type :fhir/List :id "0"
                        :entry
                        [{:fhir/type :fhir.List/entry
                          :item
                          #fhir/Reference
                              {:reference "Patient/0"}}]}]]])
             {::reitit/match {:data {:fhir.resource/type "Patient"}}
              :params {"_list" "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= #fhir/uri"/Patient/0" (-> body :entry first :fullUrl))))

        (testing "the entry has the right resource"
          (given (-> body :entry first :resource)
            :fhir/type := :fhir/Patient
            :id := "0")))))
