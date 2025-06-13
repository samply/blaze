(ns blaze.operation.graph-test
  (:require
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.fhir.spec :as fhir-spec]
   [blaze.fhir.util-spec]
   [blaze.handler.fhir.util-spec]
   [blaze.handler.util :as handler-util]
   [blaze.middleware.fhir.db :as db]
   [blaze.middleware.fhir.decrypt-page-id :as decrypt-page-id]
   [blaze.middleware.fhir.decrypt-page-id-spec]
   [blaze.operation.graph]
   [blaze.page-id-cipher.spec]
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
(log/set-min-level! :trace)
(tu/set-default-locale-english!)                            ; important for the thousands separator in 10,000

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.operation/graph nil})
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.operation/graph {}})
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))))

  (testing "invalid clock"
    (given-thrown (ig/init {:blaze.operation/graph {:clock ::invalid}})
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))
      [:cause-data ::s/problems 2 :pred] := `time/clock?
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-thrown (ig/init {:blaze.operation/graph {:rng-fn ::invalid}})
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :page-id-cipher))
      [:cause-data ::s/problems 2 :pred] := `fn?
      [:cause-data ::s/problems 2 :val] := ::invalid))

  (testing "invalid page-id-cipher"
    (given-thrown (ig/init {:blaze.operation/graph {:page-id-cipher ::invalid}})
      :key := :blaze.operation/graph
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:cause-data ::s/problems 2 :via] := [:blaze/page-id-cipher]
      [:cause-data ::s/problems 2 :val] := ::invalid)))

(def base-url "base-url-113047")
(def context-path "/context-path-173858")

(def router
  (reitit/router
   (into
    [["/Patient/{id}/__graph-page/{page-id}" {:name :Patient.operation/graph-page}]]
    (map (fn [type] [(str "/" type) {:name (keyword type "type")}]))
    ["Patient" "Condition" "Observation" "Specimen" "MedicationAdministration"])
   {:syntax :bracket
    :path context-path}))

(def match
  (reitit/map->Match
   {:data {:fhir.resource/type "Patient"}
    :path (str context-path "/Patient/0/$graph")}))

(def page-match
  (reitit/map->Match
   {:path (str context-path "/Patient/0/__graph-page")}))

(def config
  (assoc
   api-stub/mem-node-config
   :blaze.operation/graph
   {:clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)
    :page-id-cipher (ig/ref :blaze.test/page-id-cipher)}
   :blaze.test/fixed-rng-fn {}
   :blaze.test/page-id-cipher {}))

(defn wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router
            ::reitit/match match))))

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
                         handler# :blaze.operation/graph} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults
                                  (wrap-db node# page-id-cipher#)
                                  wrap-error)
             ~(or node-binding '_) node#
             ~(or page-id-cipher-binding '_) page-id-cipher#]
         ~@body))))

(comment
  (defn- page-url [page-id-cipher query-params]
    (str base-url context-path "/Patient/0/__graph-page/" (decrypt-page-id/encrypt page-id-cipher query-params)))

  (defn- page-path-params [page-id-cipher params]
    {:id "0" :page-id (decrypt-page-id/encrypt page-id-cipher params)}))

(deftest handler-test
  (testing "Patient not found"
    (with-handler [handler]
      (let [{:keys [status body]}
            @(handler {:path-params {:id "145633"}
                       :query-params {"graph" "151647"}})]

        (is (= 404 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"not-found"
          [:issue 0 :diagnostics] := "Resource `Patient/145633` was not found."))))

  (testing "Patient deleted"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]]
       [[:delete "Patient" "145711"]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "151647"}})]

        (is (= 410 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"deleted"
          [:issue 0 :diagnostics] := "Resource `Patient/145711` was deleted."))))

  (testing "GraphDefinition not-found"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]]]

      (let [{:keys [status body]}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "151647"}})]

        (is (= 400 status))

        (given body
          :fhir/type := :fhir/OperationOutcome
          [:issue 0 :severity] := #fhir/code"error"
          [:issue 0 :code] := #fhir/code"not-found"
          [:issue 0 :diagnostics] := "The graph definition `151647` was not found."))))

  (testing "only returning the patient itself"
    (with-handler [handler]
      [[[:put {:fhir/type :fhir/Patient :id "145711"}]
        [:put {:fhir/type :fhir/GraphDefinition :id "0"
               :extension
               [#fhir/Extension
                 {:url "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.start"
                  :value #fhir/id"patient"}
                #fhir/Extension
                 {:url "http://hl7.org/fhir/5.0/StructureDefinition/extension-GraphDefinition.node"
                  :extension
                  [#fhir/Extension{:url "nodeId" :value #fhir/id"patient"}
                   #fhir/Extension{:url "type" :value #fhir/code"Patient"}]}]
               :url #fhir/uri"151647"
               :name #fhir/string"patient-only"
               :status #fhir/code"active"
               :start #fhir/code"Patient"}]]]

      (let [{:keys [status] {[first-entry] :entry :as body} :body}
            @(handler {:path-params {:id "145711"}
                       :query-params {"graph" "151647"}})]

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
          (is (= (str base-url context-path "/Patient/145711")
                 (:fullUrl first-entry))))

        (testing "the entry has the right resource"
          (given (:resource first-entry)
            :fhir/type := :fhir/Patient
            :id := "145711"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))

        (testing "the entry has the right search mode"
          (given (:search first-entry)
            fhir-spec/fhir-type := :fhir.Bundle.entry/search
            :mode := #fhir/code"match"))))))
