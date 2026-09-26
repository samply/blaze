(ns blaze.fhir.spec.type-handler-ref-test
  (:require
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]])
  (:import
   [blaze.fhir.spec TypeHandlerRef]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(defn- handler [])

(deftest type-handler-ref-test
  (testing "unlinked references have no type-handler"
    (is (nil? (.get (TypeHandlerRef.)))))

  (testing "linked references have the type-handler"
    (let [ref (TypeHandlerRef.)]
      (.link ref handler)
      (is (identical? handler (.get ref)))))

  (testing "references can't be linked to no type-handler"
    (let [ref (TypeHandlerRef.)]
      (is (thrown? NullPointerException (.link ref nil)))
      (is (nil? (.get ref)))))

  (testing "references can't be linked twice"
    (let [ref (TypeHandlerRef.)
          other-handler (fn [])]
      (.link ref handler)
      (is (thrown-with-msg? IllegalStateException #"already linked"
                            (.link ref other-handler)))
      (is (identical? handler (.get ref))))))
