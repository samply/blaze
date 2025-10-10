(ns blaze.interaction.create-test
  "Specifications relevant for the FHIR create interaction:

  https://www.hl7.org/fhir/http.html#create
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
   [blaze.anomaly :as ba]
   [blaze.anomaly-spec]
   [blaze.async.comp :as ac]
   [blaze.db.api-stub :as api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.resource-store :as rs]
   [blaze.db.spec]
   [blaze.fhir.response.create-spec]
   [blaze.interaction.create]
   [blaze.interaction.test-util :refer [wrap-error]]
   [blaze.interaction.util-spec]
   [blaze.module-spec]
   [blaze.module.test-util :refer [given-failed-system with-system]]
   [blaze.test-util :as tu]
   [blaze.util.clauses-spec]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [reitit.core :as reitit]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(def base-url "base-url-134418")
(def context-path "/context-path-141016")

(def router
  (reitit/router
   [["/Patient" {:name :Patient/type}]
    ["/Observation" {:name :Observation/type}]
    ["/Bundle" {:name :Bundle/type}]]
   {:syntax :bracket
    :path context-path}))

(def config
  (assoc
   mem-node-config
   :blaze.interaction/create
   {:node (ig/ref :blaze.db/node)
    :executor (ig/ref :blaze.test/executor)
    :clock (ig/ref :blaze.test/fixed-clock)
    :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
   :blaze.test/executor {}
   :blaze.test/fixed-rng-fn {}))

(deftest init-test
  (testing "nil config"
    (given-failed-system {:blaze.interaction/create nil}
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-failed-system {:blaze.interaction/create {}}
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:cause-data ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:cause-data ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid node"
    (given-failed-system (assoc-in config [:blaze.interaction/create :node] ::invalid)
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.db/node]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid clock"
    (given-failed-system (assoc-in config [:blaze.interaction/create :clock] ::invalid)
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/clock]
      [:cause-data ::s/problems 0 :val] := ::invalid))

  (testing "invalid rng-fn"
    (given-failed-system (assoc-in config [:blaze.interaction/create :rng-fn] ::invalid)
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze/rng-fn]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(defn wrap-defaults [handler]
  (fn [request]
    (handler
     (assoc request
            :blaze/base-url base-url
            ::reitit/router router))))

(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (api-stub/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.interaction/create} config]
       ~txs
       (let [~handler-binding (-> handler# wrap-defaults wrap-error)]
         ~@body))))

(def patient-match
  (reitit/map->Match {:data {:fhir.resource/type "Patient"}}))

(def observation-match
  (reitit/map->Match {:data {:fhir.resource/type "Observation"}}))

(def bundle-match
  (reitit/map->Match {:data {:fhir.resource/type "Bundle"}}))

(deftest handler-test
  (testing "errors on"
    (testing "missing body"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match patient-match})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invalid"
            [:issue 0 :diagnostics] := #fhir/string "Missing HTTP body."))))

    (testing "type mismatch"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match patient-match
                 :body {:fhir/type :fhir/Observation}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "invariant"
            [:issue 0 :details :coding 0 :system] := #fhir/uri "http://terminology.hl7.org/CodeSystem/operation-outcome"
            [:issue 0 :details :coding 0 :code] := #fhir/code "MSG_RESOURCE_TYPE_MISMATCH"
            [:issue 0 :diagnostics] := #fhir/string "Resource type `Observation` doesn't match the endpoint type `Patient`."))))

    (testing "violated referential integrity"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                {::reitit/match observation-match
                 :body {:fhir/type :fhir/Observation :id "0"
                        :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}})]

          (is (= 409 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code "error"
            [:issue 0 :code] := #fhir/code "conflict"
            [:issue 0 :diagnostics] := #fhir/string "Referential integrity violated. Resource `Patient/0` doesn't exist."))))

    (testing "missing resource content"
      (with-redefs [rs/get (fn [_ _] (ac/completed-future nil))]
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {::reitit/match patient-match
                   :body {:fhir/type :fhir/Patient}})]

            (is (= 500 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "incomplete"
              [:issue 0 :diagnostics] := #fhir/string "The resource `Patient/AAAAAAAAAAAAAAAA` was successfully created but it's content with hash `C854DBB25D7D32AE87A7D1CD633145A775E139904408FF821FA7ABB77D311DFF` was not found during response creation."))))))

  (testing "on newly created resource"
    (testing "with no Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                {::reitit/match patient-match
                 :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= (str base-url context-path "/Patient/AAAAAAAAAAAAAAAA/_history/1")
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/Patient
            :id := "AAAAAAAAAAAAAAAA"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z")))

      (testing "Meta source is preserved"
        (with-handler [handler]
          (let [{:keys [status body]}
                @(handler
                  {::reitit/match patient-match
                   :body {:fhir/type :fhir/Patient
                          :meta #fhir/Meta{:source #fhir/uri "source-110438"}}})]

            (is (= 201 status))

            (given body
              [:meta :source] := #fhir/uri "source-110438"))))

      (testing "Meta versionId is removed"
        (with-redefs [rs/put!
                      (fn [store entries]
                        (if (nil? (:meta (first (vals entries))))
                          (rs/-put store entries)
                          (ac/completed-future (ba/fault))))]
          (with-handler [handler]
            (let [{:keys [status]}
                  @(handler
                    {::reitit/match patient-match
                     :body {:fhir/type :fhir/Patient
                            :meta #fhir/Meta{:versionId #fhir/id "1"}}})]

              (is (= 201 status)))))))

    (testing "with return=minimal Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                {::reitit/match patient-match
                 :headers {"prefer" "return=minimal"}
                 :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= (str base-url context-path "/Patient/AAAAAAAAAAAAAAAA/_history/1")
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (nil? body)))))

    (testing "with return=representation Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                {::reitit/match patient-match
                 :headers {"prefer" "return=representation"}
                 :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= (str base-url context-path "/Patient/AAAAAAAAAAAAAAAA/_history/1")
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/Patient
            :id := "AAAAAAAAAAAAAAAA"
            [:meta :versionId] := #fhir/id "1"
            [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z"))))

    (testing "with return=OperationOutcome Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                {::reitit/match patient-match
                 :headers {"prefer" "return=OperationOutcome"}
                 :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= (str base-url context-path "/Patient/AAAAAAAAAAAAAAAA/_history/1")
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (is (= :fhir/OperationOutcome (:fhir/type body)))))))

  (testing "conditional create"
    (testing "with empty header"
      (with-handler [handler]
        (let [{:keys [status]}
              @(handler
                {::reitit/match patient-match
                 :headers {"if-none-exist" ""}
                 :body {:fhir/type :fhir/Patient}})]

          (testing "a unconditional create is executed"
            (is (= 201 status))))))

    (testing "with ignorable _sort search parameter"
      (with-handler [handler]
        (let [{:keys [status]}
              @(handler
                {::reitit/match patient-match
                 :headers {"if-none-exist" "_sort=a"}
                 :body {:fhir/type :fhir/Patient}})]

          (testing "a unconditional create is executed"
            (is (= 201 status))))))

    (testing "with non-matching query"
      (testing "on empty database"
        (with-handler [handler]
          (let [{:keys [status]}
                @(handler
                  {::reitit/match patient-match
                   :headers {"if-none-exist" "identifier=212154"}
                   :body {:fhir/type :fhir/Patient}})]

            (testing "the patient is created"
              (is (= 201 status))))))

      (testing "on non-matching patient"
        (with-handler [handler]
          [[[:put {:fhir/type :fhir/Patient :id "0"
                   :identifier
                   [#fhir/Identifier{:value #fhir/string "094808"}]}]]]

          (let [{:keys [status]}
                @(handler
                  {::reitit/match patient-match
                   :headers {"if-none-exist" "identifier=212154"}
                   :body {:fhir/type :fhir/Patient}})]

            (testing "the patient is created"
              (is (= 201 status)))))))

    (testing "with matching patient"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :identifier
                 [#fhir/Identifier{:value #fhir/string "095156"}]}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match patient-match
                 :headers {"if-none-exist" "identifier=095156"}
                 :body {:fhir/type :fhir/Patient}})]

          (testing "the existing patient is returned"
            (is (= 200 status))

            (given body
              :fhir/type := :fhir/Patient
              :id := "0")))))

    (testing "with multiple matching patients"
      (with-handler [handler]
        [[[:put {:fhir/type :fhir/Patient :id "0"
                 :birthDate #fhir/date #system/date "2020"}]
          [:put {:fhir/type :fhir/Patient :id "1"
                 :birthDate #fhir/date #system/date "2020"}]]]

        (let [{:keys [status body]}
              @(handler
                {::reitit/match patient-match
                 :headers {"if-none-exist" "birthdate=2020"}
                 :body {:fhir/type :fhir/Patient}})]

          (testing "a precondition failure is returned"
            (is (= 412 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code "error"
              [:issue 0 :code] := #fhir/code "conflict"
              [:issue 0 :diagnostics] := #fhir/string "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."))))))

  (testing "with disabled referential integrity check"
    (with-system [{handler :blaze.interaction/create}
                  (assoc-in config [:blaze.db/node :enforce-referential-integrity] false)]
      (let [{:keys [status headers body]}
            @((-> handler wrap-defaults wrap-error)
              {::reitit/match observation-match
               :body {:fhir/type :fhir/Observation :id "0"
                      :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}})]

        (is (= 201 status))

        (testing "Location header"
          (is (= (str base-url context-path "/Observation/AAAAAAAAAAAAAAAA/_history/1")
                 (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Observation
          :id := "AAAAAAAAAAAAAAAA"
          [:meta :versionId] := #fhir/id "1"
          [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z"
          [:subject :reference] := #fhir/string "Patient/0"))))

  (testing "with a Bundle with references"
    (with-handler [handler]
      (let [{:keys [status headers body]}
            @(handler
              {::reitit/match bundle-match
               :body {:fhir/type :fhir/Bundle
                      :type #fhir/code "collection"
                      :entry
                      [{:fhir/type :fhir.Bundle/entry
                        :resource
                        {:fhir/type :fhir/Observation
                         :subject #fhir/Reference{:reference #fhir/string "Patient/0"}}
                        :request
                        {:fhir/type :fhir.Bundle.entry/request
                         :method #fhir/code "POST"
                         :url #fhir/uri "Observation"}}]}})]

        (is (= 201 status))

        (testing "Location header"
          (is (= (str base-url context-path "/Bundle/AAAAAAAAAAAAAAAA/_history/1")
                 (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Bundle
          :id := "AAAAAAAAAAAAAAAA"
          [:meta :versionId] := #fhir/id "1"
          [:meta :lastUpdated] := #fhir/instant #system/date-time "1970-01-01T00:00:00Z")))))
