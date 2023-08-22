(ns blaze.operation.patient.everything-test
  (:require
    [blaze.async.comp :as ac]
    [blaze.db.api-stub :as api-stub :refer [with-system-data]]
    [blaze.fhir.spec :as fhir-spec]
    [blaze.handler.util :as handler-util]
    [blaze.middleware.fhir.db :as db]
    [blaze.operation.patient.everything]
    [blaze.test-util :as tu :refer [given-thrown]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [integrant.core :as ig]
    [java-time.api :as time]
    [juxt.iota :refer [given]]
    [reitit.core :as reitit]
    [taoensso.timbre :as log])
  (:import
    [java.time Instant]))


(st/instrument)
(log/set-level! :info)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.operation.patient/everything nil})
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.operation.patient/everything {}})
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.operation.patient/everything {:clock ::invalid}})
      :key := :blaze.operation.patient/everything
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 1 :pred] := `time/clock?
      [:explain ::s/problems 1 :val] := ::invalid)))


(def base-url "base-url-113047")
(def context-path "/context-path-173858")


(def router
  (reitit/router
    (mapv (fn [type] [(str "/" type) {:name (keyword type "type")}])
          ["Patient" "Condition" "Observation" "Specimen"])
    {:syntax :bracket
     :path context-path}))


(def config
  (assoc api-stub/mem-node-config
    :blaze.operation.patient/everything
    {:clock (ig/ref :blaze.test/fixed-clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
    :blaze.test/fixed-rng-fn {}))


(defn wrap-defaults [handler]
  (fn [request]
    (handler
      (assoc request
        :blaze/base-url base-url
        ::reitit/router router))))


(defn wrap-error [handler]
  (fn [request]
    (-> (handler request)
        (ac/exceptionally handler-util/error-response))))


(defmacro with-handler [[handler-binding & [node-binding]] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{node# :blaze.db/node
                         handler# :blaze.operation.patient/everything} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults (db/wrap-db node# 100)
                                  wrap-error)
             ~(or node-binding '_) node#]
         ~@body))))


(deftest handler-test
  (testing "Patient not found"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 0"
          (is (= #fhir/unsignedInt 0 (:total body))))

        (testing "the bundle contains no entry"
          (is (empty? (:entry body)))))))

  (testing "Patient deleted"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]]
       [[:delete "Patient" "0"]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 0"
          (is (= #fhir/unsignedInt 0 (:total body))))

        (testing "the bundle contains no entry"
          (is (empty? (:entry body)))))))

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
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 1"
          (is (= #fhir/unsignedInt 1 (:total body))))

        (testing "the bundle contains one entry"
          (is (= 1 (count (:entry body)))))

        (testing "the entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (:fullUrl first-entry))))

        (testing "the entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code"match")))))

  (doseq [type ["Observation" "Specimen"]]
    (testing (str "Patient with one " type)
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"}]
          [:put {:fhir/type (keyword "fhir" type) :id "0"
                 :subject #fhir/Reference{:reference "Patient/0"}}]]]

        (let [{:keys [status] {[first-entry second-entry] :entry :as body} :body}
              @(handler {:path-params {:id "0"}})]

          (is (= 200 status))

          (testing "the body contains a bundle"
            (is (= :fhir/Bundle (:fhir/type body))))

          (testing "the bundle id is an LUID"
            (is (= "AAAAAAAAAAAAAAAA" (:id body))))

          (testing "the bundle type is searchset"
            (is (= #fhir/code"searchset" (:type body))))

          (testing "the total count is 2"
            (is (= #fhir/unsignedInt 2 (:total body))))

          (testing "the bundle contains two entries"
            (is (= 2 (count (:entry body)))))

          (testing "the first entry has the right fullUrl"
            (is (= (str base-url context-path "/Patient/0")
                   (:fullUrl first-entry))))

          (testing "the first entry has the right resource"
            (given (:resource first-entry)
              :fhir/type := :fhir/Patient
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the first entry has the right search mode"
            (given (:search first-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code"match"))

          (testing "the second entry has the right fullUrl"
            (is (= (str base-url context-path (format "/%s/0" type))
                   (:fullUrl second-entry))))

          (testing "the second entry has the right resource"
            (given (:resource second-entry)
              :fhir/type := (keyword "fhir" type)
              :id := "0"
              [:meta :versionId] := #fhir/id"1"
              [:meta :lastUpdated] := Instant/EPOCH))

          (testing "the second entry has the right search mode"
            (given (:search second-entry)
              fhir-spec/fhir-type := :fhir.Bundle.entry/search
              :mode := #fhir/code"match"))))))

  (testing "Patient with two Observations"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Observation :id "1"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [{:keys [status]
             {[first-entry second-entry third-entry] :entry :as body} :body}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 3"
          (is (= #fhir/unsignedInt 3 (:total body))))

        (testing "the bundle contains three entries"
          (is (= 3 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (:fullUrl first-entry))))

        (testing "the first entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the first entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code"match"))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/0")
                 (:fullUrl second-entry))))

        (testing "the second entry has the right resource"
          (given (:resource second-entry)
            :fhir/type := :fhir/Observation
            :id := "0"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the second entry has the right search mode"
          (given (:search second-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code"match"))

        (testing "the third entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/1")
                 (:fullUrl third-entry))))

        (testing "the third entry has the right resource"
          (given (:resource third-entry)
            :fhir/type := :fhir/Observation
            :id := "1"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the third entry has the right search mode"
          (given (:search third-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code"match")))))

  (testing "Patient with various resources"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "0"}]
        [:put {:fhir/type :fhir/Observation :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Condition :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]
        [:put {:fhir/type :fhir/Specimen :id "0"
               :subject #fhir/Reference{:reference "Patient/0"}}]]]

      (let [{:keys [status]
             {[first-entry second-entry third-entry forth-entry] :entry
              :as body} :body}
            @(handler {:path-params {:id "0"}})]

        (is (= 200 status))

        (testing "the body contains a bundle"
          (is (= :fhir/Bundle (:fhir/type body))))

        (testing "the bundle id is an LUID"
          (is (= "AAAAAAAAAAAAAAAA" (:id body))))

        (testing "the bundle type is searchset"
          (is (= #fhir/code"searchset" (:type body))))

        (testing "the total count is 4"
          (is (= #fhir/unsignedInt 4 (:total body))))

        (testing "the bundle contains four entries"
          (is (= 4 (count (:entry body)))))

        (testing "the first entry has the right fullUrl"
          (is (= (str base-url context-path "/Patient/0")
                 (:fullUrl first-entry))))

        (testing "the second entry has the right fullUrl"
          (is (= (str base-url context-path "/Condition/0")
                 (:fullUrl second-entry))))

        (testing "the third entry has the right fullUrl"
          (is (= (str base-url context-path "/Observation/0")
                 (:fullUrl third-entry))))

        (testing "the forth entry has the right fullUrl"
          (is (= (str base-url context-path "/Specimen/0")
                 (:fullUrl forth-entry)))))))

  (testing "to many resources"
    (with-handler [handler]
      [(into
         [[:put {:fhir/type :fhir/Patient :id "0"}]]
         (map (fn [i]
                [:put {:fhir/type :fhir/Observation :id (str i)
                       :subject #fhir/Reference{:reference "Patient/0"}}]))
         (range 10000))]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "0"}})]

        (is (= 409 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"too-costly"
          [:issue 0 :diagnostics] := "The compartment of the Patient with the id `0` has more than 10000 resources which is too costly to output. Please use type search with rev-include or compartment search instead.")))))
