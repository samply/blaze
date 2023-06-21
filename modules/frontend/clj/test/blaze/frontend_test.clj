(ns blaze.frontend-test
  (:require
    [blaze.frontend]
    [blaze.test-util :as tu :refer [given-thrown with-system]]
    [blaze.test-util.ring :refer [call]]
    [clojure.spec.alpha :as s]
    [clojure.spec.test.alpha :as st]
    [clojure.string :as str]
    [clojure.test :as test :refer [deftest testing]]
    [integrant.core :as ig]
    [juxt.iota :refer [given]]
    [taoensso.timbre :as log])
  (:import
    [java.io File]))


(set! *warn-on-reflection* true)
(st/instrument)
(log/set-level! :trace)


(test/use-fixtures :each tu/fixture)


(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze/frontend nil})
      :key := :blaze/frontend
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `map?))

  (testing "invalid context-path"
    (given-thrown (ig/init {:blaze/frontend {:context-path ::invalid}})
      :key := :blaze/frontend
      :reason := ::ig/build-failed-spec
      [:explain ::s/problems 0 :pred] := `string?
      [:explain ::s/problems 0 :val] := ::invalid)))


(def config
  {:blaze/frontend
   {:context-path "/fhir"}})


(def ^String assets-path "/__frontend/immutable/assets")


(def css-file? #(and (.isFile ^File %) (str/ends-with? (.getName ^File %) "css")))


(deftest handler-test
  (with-system [{handler :blaze/frontend} config]
    (given (call handler {:uri "/fhir" :request-method :get})
      :status := 200
      [:body str] :# #".+/public/index.html$"
      [:headers "Content-Type"] := "text/html"
      [:headers "Cache-Control"] := "no-cache")

    (given (call handler {:uri "/fhir/__frontend/version.json" :request-method :get})
      :status := 200
      [:body str] :# #".+/public/__frontend/version.json$"
      [:headers "Content-Type"] := "application/json"
      [:headers "Cache-Control"] := "no-cache")

    (testing "CSS assets"
      (doseq [file (filter css-file? (file-seq (File. "build/public" assets-path)))]
        (given (call handler {:uri (str "/fhir" assets-path "/" (.getName file)) :request-method :get})
          :status := 200
          [:headers "Content-Type"] := "text/css"
          [:headers "Cache-Control"] := "public, max-age=604800, immutable")))))
