(ns blaze.rest-api.metadata-handler-test
  (:require
   [blaze.fhir.structure-definition-repo-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.rest-api.metadata-handler]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.rest-api/metadata-handler nil})
      :key := :blaze.rest-api/metadata-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.rest-api/metadata-handler {}})
      :key := :blaze.rest-api/metadata-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :structure-definition-repo))))

  (testing "invalid executor"
    (given-thrown (ig/init {:blaze.rest-api/metadata-handler {:structure-definition-repo ::invalid}})
      :key := :blaze.rest-api/metadata-handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :via] := [:blaze.fhir/structure-definition-repo]
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  {:blaze.rest-api/metadata-handler
   {:structure-definition-repo (ig/ref :blaze.fhir/structure-definition-repo)}

   :blaze.fhir/structure-definition-repo {}})

(defmacro with-handler [[handler-binding] & body]
  `(with-system [{handler# :blaze.rest-api/metadata-handler} config]
     (let [~handler-binding handler#]
       ~@body)))

(deftest handler-test
  (with-handler [handler]
    (testing "not-found"
      (given @(handler {:params {"url" "foo"}})
        :status := 404
        :body := {}))

    (testing "success"
      (given @(handler {:params {"url" "http://hl7.org/fhir/StructureDefinition/Patient"}})
        :status := 200
        [:body :name] := "Patient"))))
