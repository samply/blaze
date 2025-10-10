(ns blaze.operation.patient.everything-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.test-util :refer [link-url]]
   [blaze.handler.fhir.util-spec]
   [blaze.handler.util :as handler-util]
   [blaze.interaction.search.util :as search-util]
   [blaze.interaction.search.util-spec]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system]]
   [blaze.operation.patient.everything]
   [blaze.page-id-cipher.spec]
   [blaze.spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log])
  (:import
   [java.time Instant]))

(st/instrument)
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.operation.patient/everything
   {::search-util/link (ig/ref ::search-util/link)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   ::search-util/link {:fhir/version "4.0.1"}
   :blaze.test/fixed-rng-fn {}
   :blaze.test/page-id-cipher {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.operation.patient/everything nil}
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.operation.patient/everything {}}
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% ::search-util/link))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid link function"
    (given-failed-system (assoc-in config [:blaze.operation.patient/everything ::search-util/link] ::invalid)
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [::search-util/link]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.operation.patient/everything :clock] ::invalid)
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.operation.patient/everything :rng-fn] ::invalid)
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-failed-system (assoc-in config [:blaze.operation.patient/everything :page-id-cipher] ::invalid)
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/page-id-cipher]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def base-url "base-url-113047")
(def context-path "/context-path-173858")

(def router
  (reitit/router
   (into
    [["/Patient/{id}/__everything-page/{page-id}" {:name :Patient.operation/everything-page}]]
    (map (fn [type] [(str "/" type) {:name (keyword type "type")}]))
    ["Patient" "Condition" "Observation" "Specimen" "MedicationAdministration"])
   {:syntax :bracket
    :path context-path}))

(def match
  (reitit/map->Match
   {:path (str context-path "/Patient/0/$everything")}))

(def page-match
  (reitit/map->Match
   {:path (str context-path "/Patient/0/__everything-page")}))

(defn wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router))))

(defn- wrap-db [handler node page-id-cipher]
  (fn [{::reitit/keys [match] :as request}]
    (if (= page-match match)
      ((decrypt-page-id/wrap-decrypt-page-id
        (db/wrap-snapshot-db handler node 100)
        page-id-cipher)
       request)
      ((db/wrap-db handler node 100) request))))

(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))

(defmacro with-handler [[handler-binding & [node-binding page-id-cipher-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         page-id-cipher# :blaze.test/page-id-cipher
                         handler# :blaze.operation.patient/everything} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults
                                  (wrap-db node# page-id-cipher#)
                                  wrap-error)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(defn- page-url [page-id-cipher query-params]
  (str base-url context-path "/Patient/0/__everything-page/" (decrypt-page-id/encrypt page-id-cipher query-params)))

(defn- page-path-params [page-id-cipher params]
  {:id "0" :page-id (decrypt-page-id/encrypt page-id-cipher params)})

(deftest handler-test
  (testing "Patient not found"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:path-params {:id "145801"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "not-found"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/145801` was not found."))))

  (testing "Patient deleted"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "150158"}]]
       [[:delete "Patient" "150158"]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "150158"}})]

        (is (= 410 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "deleted"
          [:issue 0 :diagnostics] := #fhir/string "Resource `Patient/150158` was deleted."))))

  (testing "invalid start date"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"start" "invalid"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "The value `invalid` of the query param `start` is no valid date."))))

  (testing "invalid end date"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}
                       :query-params {"end" "invalid"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "invalid"
          [:issue 0 :diagnostics] := #fhir/string "The value `invalid` of the query param `end` is no valid date."))))

  (testing "Patient only"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (-> first-entry :fullUrl :value))))

        (testing "the entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (doseq [type ["Observation" "Specimen"]]
    (testing (str "Patient with one " type)
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type (keyword "fhir" type) :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler {:path-params {:id "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the first entry has the right search mode"
            (given (:search first-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code "match"))

          (testing "the second entry has the right fullUrl"
            (is (= (str base-url context-path (format "/%s/0" type))
                   (-> second-entry :fullUrl :value))))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := (keyword "fhir" type)
              :id := "0"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the second entry has the right search mode"
            (given (:search second-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code "match"))))))

  (testing "Patient with two Observations"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [{:keys [status]
             {[first-entry second-entry third-entry] :entry :as body} :body}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 3"
          (is (= #fhir/unsignedInt 3 (:total body))))

        (testing "the bundle contains three entries"
          (is (= 3 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (-> first-entry :fullUrl :value))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the first entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/0")
                 (-> second-entry :fullUrl :value))))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "0"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the third entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/1")
                 (-> third-entry :fullUrl :value))))

        (testing "the third entry has the right resource"
          (given (:resource third-entry)
            :fhir/type := :fhir/Observation
            :id := "1"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the third entry has the right search mode"
          (given (:search third-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (testing "with start date"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :effective #fhir/dateTime "2024-01-04T23:45:50Z"}]]]

      (let [{:keys [status]
             {[first-entry second-entry] :entry :as body} :body}
            @(handler {:path-params {:id "0"}
                       :query-params {"start" "2024"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (-> first-entry :fullUrl :value))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the first entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/1")
                 (-> second-entry :fullUrl :value))))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "1"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (testing "with end date"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
               :effective #fhir/dateTime "2024-01-04T23:45:50Z"}]]]

      (let [{:keys [status]
             {[first-entry second-entry] :entry :as body} :body}
            @(handler {:path-params {:id "0"}
                       :query-params {"end" "2024"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (-> first-entry :fullUrl :value))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the first entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match"))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/1")
                 (-> second-entry :fullUrl :value))))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "1"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code "match")))))

  (testing "Patient with various resources"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Condition :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
        [:put {:fhir/type :fhir/Specimen :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [{:keys [status]
             {[first-entry second-entry third-entry fourth-entry] :entry
              :as body} :body}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 4"
          (is (= #fhir/unsignedInt 4 (:total body))))

        (testing "the bundle contains four entries"
          (is (= 4 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (-> first-entry :fullUrl :value))))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Condition/0")
                 (-> second-entry :fullUrl :value))))

        (testing "the third entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/0")
                 (-> third-entry :fullUrl :value))))

        (testing "the fourth entry has the right fullUrl"
          (is (= (str base-url context-path "/Specimen/0")
                 (-> fourth-entry :fullUrl :value)))))))

  (testing "Patient with MedicationAdministration because it is reachable twice
            via the search param `patient` and `subject`.

            This test should assure that MedicationAdministration resources are
            returned only once."
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/MedicationAdministration :id "0"
               :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]]]

      (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is 2"
          (is (= #fhir/unsignedInt 2 (:total body))))

        (testing "the bundle contains four entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (-> first-entry :fullUrl :value))))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/MedicationAdministration/0")
                 (-> second-entry :fullUrl :value)))))))

  (testing "to many resources"
    (with-handler [handler]
      [(into
        [[:put {:fhir/type :fhir/Patient :id "0"}]]
        (map (fn [i]
               [:put {:fhir/type :fhir/Observation :id (str i)
                      :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]))
        (range 10000))]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 409 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code "error"
          [:issue 0 :code] := #fhir/code "too-costly"
          [:issue 0 :diagnostics] := #fhir/string "The compartment of the Patient with the id `0` has more than 10,000 resources which is too costly to output. Please use paging by specifying the _count query param."))))

  (testing "paging"
    (with-handler [handler _ page-id-cipher]
      [(into
        [[:put {:fhir/type :fhir/Patient :id "0"}]]
        (map (fn [idx]
               [:put {:fhir/type :fhir/Observation :id (str idx)
                      :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]))
        (range 4))]

      (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
            @(handler {::reitit/match match
                       :path-params {:id "0"}
                       :query-params {"_count" "2"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is not given"
          (is (nil? (:total body))))

        (testing "has a next link"
          (is (= (page-url page-id-cipher {"_count" "2" "__t" "1" "__page-offset" "2"})
                 (link-url body "next"))))

        (testing "the bundle contains two entries"
          (is (= 2 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (-> first-entry :fullUrl :value))))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/0")
                 (-> second-entry :fullUrl :value)))))

      (testing "following the first next link"
        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler
                {::reitit/match page-match
                 :path-params
                 (page-path-params
                  page-id-cipher
                  {"_count" "2" "__t" "1" "__page-offset" "2"})})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is not given"
            (is (nil? (:total body))))

          (testing "has a next link"
            (is (= (page-url page-id-cipher {"_count" "2" "__t" "1" "__page-offset" "4"})
                   (link-url body "next"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry has the right fullUrl"
            (is (= (str base-url context-path "/Observation/1")
                   (-> first-entry :fullUrl :value))))

          (testing "the second entry has the right fullUrl"
            (is (= (str base-url context-path "/Observation/2")
                   (-> second-entry :fullUrl :value))))))

      (testing "following the second next link"
        (let [{:keys [status] {[entry] :entry :as body} :body}
              @(handler
                {::reitit/match page-match
                 :path-params
                 (page-path-params
                  page-id-cipher
                  {"_count" "2" "__t" "1" "__page-offset" "4"})})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is not given"
            (is (nil? (:total body))))

          (testing "has no next link"
            (is (nil? (link-url body "next"))))

          (testing "the bundle contains one entry"
            (is (= 1 (count (:entry body)))))

          (testing "the entry has the right fhir type"
            (is (= :fhir.Bundle/entry (:fhir/type entry))))

          (testing "the entry has the right fullUrl"
            (is (= (str base-url context-path "/Observation/3")
                   (-> entry :fullUrl :value)))))))

    (testing "with start date"
      (with-handler [handler _ page-id-cipher]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                 :effective #fhir/dateTime "2024-01-04T23:45:50Z"}]
          [:put {:fhir/type :fhir/Observation :id "2"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                 :effective #fhir/dateTime "2024-01-05T23:45:50Z"}]]]

        (let [{:keys [status]
               {[first-entry second-entry] :entry :as body} :body}
              @(handler {:path-params {:id "0"}
                         :query-params {"start" "2024" "_count" "2"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is not given"
            (is (nil? (:total body))))

          (testing "has a next link"
            (is (= (page-url page-id-cipher {"_count" "2" "__t" "1" "__page-offset" "2" "start" "2024"})
                   (link-url body "next"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the first entry has the right search mode"
            (given (:search first-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code "match"))

          (testing "the second entry has the right fullUrl"
            (is (= (str base-url context-path "/Observation/1")
                   (-> second-entry :fullUrl :value))))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Observation
              :id := "1"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the second entry has the right search mode"
            (given (:search second-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code "match")))

        (testing "following the next link"
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler
                  {::reitit/match page-match
                   :path-params
                   (page-path-params
                    page-id-cipher
                    {"_count" "2" "__t" "1" "__page-offset" "2" "start" "2024"})})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle id is an LUID"
              (is (= "AAAAAAAAAAAAAAAA" (:id body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is not given"
              (is (nil? (:total body))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry has the right fullUrl"
              (is (= (str base-url context-path "/Observation/2")
                     (-> first-entry :fullUrl :value))))))))

    (testing "with start and end date"
      (with-handler [handler _ page-id-cipher]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type :fhir/Observation :id "0"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]
          [:put {:fhir/type :fhir/Observation :id "1"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                 :effective #fhir/dateTime "2024-01-04T23:45:50Z"}]
          [:put {:fhir/type :fhir/Observation :id "2"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                 :effective #fhir/dateTime "2024-01-05T23:45:50Z"}]
          [:put {:fhir/type :fhir/Observation :id "3"
                 :subject #fhir/Reference{:reference #fhir/string "Patient/0"}
                 :effective #fhir/dateTime "2026-01-05T23:45:50Z"}]]]

        (let [{:keys [status]
               {[first-entry second-entry] :entry :as body} :body}
              @(handler {:path-params {:id "0"}
                         :query-params {"start" "2024" "end" "2025" "_count" "2"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code "searchset" (:type body))))

          (testing "the total count is not given"
            (is (nil? (:total body))))

          (testing "has a next link"
            (is (= (page-url page-id-cipher {"_count" "2" "__t" "1" "__page-offset" "2"
                                             "start" "2024" "end" "2025"})
                   (link-url body "next"))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (-> first-entry :fullUrl :value))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the first entry has the right search mode"
            (given (:search first-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code "match"))

          (testing "the second entry has the right fullUrl"
            (is (= (str base-url context-path "/Observation/1")
                   (-> second-entry :fullUrl :value))))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := :fhir/Observation
              :id := "1"
              [:meta :versionId] := #fhir/id "1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the second entry has the right search mode"
            (given (:search second-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code "match")))

        (testing "following the next link"
          (let [{:keys [status] {[first-entry] :entry :as body} :body}
                @(handler
                  {::reitit/match page-match
                   :path-params
                   (page-path-params
                    page-id-cipher
                    {"_count" "2" "__t" "1" "__page-offset" "2"
                     "start" "2024" "end" "2025"})})]

            (is (= 200 status))

            (testing "the body contains a bundle"
              (is (= :fhir/Bundle (:fhir/type body))))

            (testing "the bundle id is an LUID"
              (is (= "AAAAAAAAAAAAAAAA" (:id body))))

            (testing "the bundle type is searchset"
              (is (= #fhir/code "searchset" (:type body))))

            (testing "the total count is not given"
              (is (nil? (:total body))))

            (testing "has no next link"
              (is (nil? (link-url body "next"))))

            (testing "the bundle contains one entry"
              (is (= 1 (count (:entry body)))))

            (testing "the entry has the right fullUrl"
              (is (= (str base-url context-path "/Observation/2")
                     (-> first-entry :fullUrl :value)))))))))

  (testing "page size of 10,000"
    (with-handler [handler _ page-id-cipher]
      [(into
        [[:put {:fhir/type :fhir/Patient :id "0"}]]
        (map (fn [i]
               [:put {:fhir/type :fhir/Observation :id (str i)
                      :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}]))
        (range 20000))]

      (let [{:keys [status body]}
            @(handler {::reitit/match match
                       :path-params {:id "0"}
                       :query-params {"_count" "10000"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code "searchset" (:type body))))

        (testing "the total count is not given"
          (is (nil? (:total body))))

        (testing "has a next link"
          (is (= (page-url page-id-cipher {"_count" "10000" "__t" "1" "__page-offset" "10000"})
                 (link-url body "next"))))

        (testing "the bundle contains 10,000 entries"
          (is (= 10000 (count (:entry body)))))))))
