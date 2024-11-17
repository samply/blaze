(ns blaze.metrics.handler-test
  (:require
   [blaze.metrics.handler]
   [blaze.metrics.registry]
   [blaze.metrics.spec :as spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [given-thrown]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest testing]]
   [integrant.core :as ig]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest init-test
  (testing "nil config"
    (given-thrown (ig/init {:blaze.metrics/handler nil})
      :key := :blaze.metrics/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `map?))

  (testing "missing config"
    (given-thrown (ig/init {:blaze.metrics/handler {}})
      :key := :blaze.metrics/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `(fn ~'[%] (contains? ~'% :registry))))

  (testing "invalid registry"
    (given-thrown (ig/init {:blaze.metrics/handler {:registry ::invalid}})
      :key := :blaze.metrics/handler
      :reason := ::ig/build-failed-spec
      [:cause-data ::s/problems 0 :pred] := `spec/registry?
      [:cause-data ::s/problems 0 :val] := ::invalid)))

(def config
  {:blaze.metrics/handler {:registry (ig/ref :blaze.metrics/registry)}
   :blaze.metrics/registry {}})

(deftest handler-test
  (with-system [{:blaze.metrics/keys [handler]} config]
    (given (handler nil)
      :status := 200
      [:headers "Content-Type"] := "text/plain; version=0.0.4; charset=utf-8"
      :body :? #(str/starts-with? % "# HELP"))))
