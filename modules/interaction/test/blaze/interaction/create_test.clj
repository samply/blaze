(ns blaze.interaction.create-test
  "Specifications relevant for the FHIR create interaction:

  https://www.hl7.org/fhir/http.html#create
  https://www.hl7.org/fhir/operationoutcome.html
  https://www.hl7.org/fhir/http.html#ops"
  (:require
    [blaze.anomaly-spec]
    [blaze.db.api-stub
     :refer [create-mem-node-system with-system-data]]
    [blaze.executors :as ex]
    [blaze.fhir.response.create-spec]
    [blaze.fhir.spec.type]
    [blaze.interaction.create]
    [blaze.interaction.test-util :as itu :refer [wrap-error]]
    [blaze.interaction.util-spec]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
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
(tu/init-fhir-specs)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(def base-url "base-url-134418")


(def router
  (reitit/router
    [["/Patient" {:name :Patient/type}]
     ["/Observation" {:name :Observation/type}]
     ["/Bundle" {:name :Bundle/type}]]
    {:syntax :bracket}))


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.interaction/create nil})
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.interaction/create {}})
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :executor))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 3 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))))

  (testing "invalid executor"
    (given-thrown (ig/init {:blaze.interaction/create {:executor ::invalid}})
      :key := :blaze.interaction/create
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :node))
      [:explain ::s/problems 1 :pred] := `(fn ~'[%] (contains? ~'% :clock))
      [:explain ::s/problems 2 :pred] := `(fn ~'[%] (contains? ~'% :rng-fn))
      [:explain ::s/problems 3 :pred] := `ex/executor?
      [:explain ::s/problems 3 :val] := ::invalid)))


(defn create-system [node-config]
  (assoc (create-mem-node-system node-config)
    :blaze.interaction/create
    {:node (ig/ref :blaze.db/node)
     :executor (ig/ref :blaze.test/executor)
     :clock (ig/ref :blaze.test/clock)
     :rng-fn (ig/ref :blaze.test/fixed-rng-fn)}
    :blaze.test/executor {}
    :blaze.test/fixed-rng-fn {}))


(def system
  (create-system {}))


(defn wrap-defaults [handler]
  (fn [request]
    (handler
      (assoc request
        :blaze/base-url base-url
        ::reitit/router router))))


(defmacro with-handler [[handler-binding] & more]
  (let [[txs body] (itu/extract-txs-body more)]
    `(with-system-data [{handler# :blaze.interaction/create} system]
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
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invalid"
            [:issue 0 :diagnostics] := "Missing HTTP body."))))

    (testing "type mismatch"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :body {:fhir/type :fhir/Observation}})]

          (is (= 400 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"invariant"
            [:issue 0 :details :coding 0 :system] := #fhir/uri"http://terminology.hl7.org/CodeSystem/operation-outcome"
            [:issue 0 :details :coding 0 :code] := #fhir/code"MSG_RESOURCE_TYPE_MISMATCH"
            [:issue 0 :diagnostics] := "Resource type `Observation` doesn't match the endpoint type `Patient`."))))

    (testing "violated referential integrity"
      (with-handler [handler]
        (let [{:keys [status body]}
              @(handler
                 {::reitit/match observation-match
                  :body {:fhir/type :fhir/Observation :id "0"
                         :subject #fhir/Reference{:reference "Patient/0"}}})]

          (is (= 409 status))

          (given body
            :fhir/type := :fhir/OperationOutcome
            [:issue 0 :severity] := #fhir/code"error"
            [:issue 0 :code] := #fhir/code"conflict"
            [:issue 0 :diagnostics] := "Referential integrity violated. Resource `Patient/0` doesn't exist.")))))

  (testing "on newly created resource"
    (testing "with no Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                 {::reitit/match patient-match
                  :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= (str base-url "/Patient/AAAAAAAAAAAAAAAA/_history/1")
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/Patient
            :id := "AAAAAAAAAAAAAAAA"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))))

    (testing "with return=minimal Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                 {::reitit/match patient-match
                  :headers {"prefer" "return=minimal"}
                  :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= (str base-url "/Patient/AAAAAAAAAAAAAAAA/_history/1")
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
            (is (= (str base-url "/Patient/AAAAAAAAAAAAAAAA/_history/1")
                   (get headers "Location"))))

          (testing "Transaction time in Last-Modified header"
            (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

          (testing "Version in ETag header"
            ;; 1 is the T of the transaction of the resource creation
            (is (= "W/\"1\"" (get headers "ETag"))))

          (given body
            :fhir/type := :fhir/Patient
            :id := "AAAAAAAAAAAAAAAA"
            [:meta :versionId] := #fhir/id"1"
            [:meta :lastUpdated] := Instant/EPOCH))))

    (testing "with return=OperationOutcome Prefer header"
      (with-handler [handler]
        (let [{:keys [status headers body]}
              @(handler
                 {::reitit/match patient-match
                  :headers {"prefer" "return=OperationOutcome"}
                  :body {:fhir/type :fhir/Patient}})]

          (is (= 201 status))

          (testing "Location header"
            (is (= (str base-url "/Patient/AAAAAAAAAAAAAAAA/_history/1")
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
                   [#fhir/Identifier{:value "094808"}]}]]]

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
                 [#fhir/Identifier{:value "095156"}]}]]]

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
                 :birthDate #fhir/date"2020"}]
          [:put {:fhir/type :fhir/Patient :id "1"
                 :birthDate #fhir/date"2020"}]]]

        (let [{:keys [status body]}
              @(handler
                 {::reitit/match patient-match
                  :headers {"if-none-exist" "birthdate=2020"}
                  :body {:fhir/type :fhir/Patient}})]

          (testing "a precondition failure is returned"
            (is (= 412 status))

            (given body
              :fhir/type := :fhir/OperationOutcome
              [:issue 0 :severity] := #fhir/code"error"
              [:issue 0 :code] := #fhir/code"conflict"
              [:issue 0 :diagnostics] := "Conditional create of a Patient with query `birthdate=2020` failed because at least the two matches `Patient/0/_history/1` and `Patient/1/_history/1` were found."))))))

  (testing "with disabled referential integrity check"
    (with-system [{handler :blaze.interaction/create} (create-system {:enforce-referential-integrity false})]
      (let [{:keys [status headers body]}
            @((-> handler wrap-defaults wrap-error)
              {::reitit/match observation-match
               :body {:fhir/type :fhir/Observation :id "0"
                      :subject #fhir/Reference{:reference "Patient/0"}}})]

        (is (= 201 status))

        (testing "Location header"
          (is (= (str base-url "/Observation/AAAAAAAAAAAAAAAA/_history/1")
                 (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Observation
          :id := "AAAAAAAAAAAAAAAA"
          [:meta :versionId] := #fhir/id"1"
          [:meta :lastUpdated] := Instant/EPOCH
          [:subject :reference] := "Patient/0"))))

  (testing "with a Bundle with references"
    (with-handler [handler]
      (let [{:keys [status headers body]}
            @(handler
               {::reitit/match bundle-match
                :body {:fhir/type :fhir/Bundle
                       :type #fhir/code"collection"
                       :entry
                       [{:fhir/type :fhir.Bundle/entry
                         :resource
                         {:fhir/type :fhir/Observation
                          :subject #fhir/Reference{:reference "Patient/0"}}
                         :request
                         {:fhir/type :fhir.Bundle.entry/request
                          :method #fhir/code"POST"
                          :url #fhir/uri"Observation"}}]}})]

        (is (= 201 status))

        (testing "Location header"
          (is (= (str base-url "/Bundle/AAAAAAAAAAAAAAAA/_history/1")
                 (get headers "Location"))))

        (testing "Transaction time in Last-Modified header"
          (is (= "Thu, 1 Jan 1970 00:00:00 GMT" (get headers "Last-Modified"))))

        (testing "Version in ETag header"
          ;; 1 is the T of the transaction of the resource creation
          (is (= "W/\"1\"" (get headers "ETag"))))

        (given body
          :fhir/type := :fhir/Bundle
          :id := "AAAAAAAAAAAAAAAA"
          [:meta :versionId] := #fhir/id"1"
          [:meta :lastUpdated] := Instant/EPOCH)))))
