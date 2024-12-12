(ns blaze.terminology-service.local.code-system.sct.type-test
  (:require
   [blaze.terminology-service.local.code-system.sct.type :refer [parse-sctid]]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest parse-sctid-test
  (is (nil? (parse-sctid "0815"))))
