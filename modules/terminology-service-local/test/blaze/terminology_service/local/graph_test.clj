(ns blaze.terminology-service.local.graph-test
  (:require
   [blaze.fhir.test-util]
   [blaze.terminology-service.local.graph :as graph]
   [blaze.terminology-service.local.graph-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]
   [juxt.iota :refer [given]]
   [taoensso.timbre :as log]))

(st/instrument)
(log/set-min-level! :trace)

(test/use-fixtures :each tu/fixture)

(deftest build-graph-test
  (testing "empty list of concepts"
    (given (graph/build-graph [])
      :concepts := {}
      :child-index := {}))

  (testing "one root concept"
    (given (graph/build-graph
            [{:fhir/type :fhir.CodeSystem/concept
              :code #fhir/code "code-180828"}])
      [:concepts "code-180828"] := {:fhir/type :fhir.CodeSystem/concept
                                    :code #fhir/code "code-180828"}
      :child-index := {}))

  (testing "two root concepts"
    (given (graph/build-graph
            [{:fhir/type :fhir.CodeSystem/concept
              :code #fhir/code "code-180828"}
             {:fhir/type :fhir.CodeSystem/concept
              :code #fhir/code "code-162632"}])
      [:concepts "code-180828"] := {:fhir/type :fhir.CodeSystem/concept
                                    :code #fhir/code "code-180828"}
      [:concepts "code-162632"] := {:fhir/type :fhir.CodeSystem/concept
                                    :code #fhir/code "code-162632"}
      :child-index := {}))

  (testing "one root and one child"
    (testing "with hierarchy"
      (given (graph/build-graph
              [{:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-180828"
                :concept
                [{:fhir/type :fhir.CodeSystem/concept
                  :code #fhir/code "code-191445"}]}])
        [:concepts "code-180828"] := {:fhir/type :fhir.CodeSystem/concept
                                      :code #fhir/code "code-180828"
                                      :property
                                      [{:fhir/type :fhir.CodeSystem.concept/property
                                        :code #fhir/code "child"
                                        :value #fhir/code "code-191445"}]}
        [:concepts "code-191445"] := {:fhir/type :fhir.CodeSystem/concept
                                      :code #fhir/code "code-191445"
                                      :property
                                      [{:fhir/type :fhir.CodeSystem.concept/property
                                        :code #fhir/code "parent"
                                        :value #fhir/code "code-180828"}]}
        :child-index := {"code-180828" #{"code-191445"}}))

    (testing "with parent property only"
      (given (graph/build-graph
              [{:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-180828"}
               {:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-191445"
                :property
                [{:fhir/type :fhir.CodeSystem.concept/property
                  :code #fhir/code "parent"
                  :value #fhir/code "code-180828"}]}])
        [:concepts "code-180828"] := {:fhir/type :fhir.CodeSystem/concept
                                      :code #fhir/code "code-180828"
                                      :property
                                      [{:fhir/type :fhir.CodeSystem.concept/property
                                        :code #fhir/code "child"
                                        :value #fhir/code "code-191445"}]}
        [:concepts "code-191445"] := {:fhir/type :fhir.CodeSystem/concept
                                      :code #fhir/code "code-191445"
                                      :property
                                      [{:fhir/type :fhir.CodeSystem.concept/property
                                        :code #fhir/code "parent"
                                        :value #fhir/code "code-180828"}]}
        :child-index := {"code-180828" #{"code-191445"}}))

    (testing "with child property only"
      (given (graph/build-graph
              [{:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-180828"
                :property
                [{:fhir/type :fhir.CodeSystem.concept/property
                  :code #fhir/code "child"
                  :value #fhir/code "code-191445"}]}
               {:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-191445"}])
        [:concepts "code-180828"] := {:fhir/type :fhir.CodeSystem/concept
                                      :code #fhir/code "code-180828"
                                      :property
                                      [{:fhir/type :fhir.CodeSystem.concept/property
                                        :code #fhir/code "child"
                                        :value #fhir/code "code-191445"}]}
        [:concepts "code-191445"] := {:fhir/type :fhir.CodeSystem/concept
                                      :code #fhir/code "code-191445"
                                      :property
                                      [{:fhir/type :fhir.CodeSystem.concept/property
                                        :code #fhir/code "parent"
                                        :value #fhir/code "code-180828"}]}
        :child-index := {"code-180828" #{"code-191445"}}))

    (testing "with both the parent property and the child property"
      (given (graph/build-graph
              [{:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-180828"
                :property
                [{:fhir/type :fhir.CodeSystem.concept/property
                  :code #fhir/code "child"
                  :value #fhir/code "code-191445"}]}
               {:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-191445"
                :property
                [{:fhir/type :fhir.CodeSystem.concept/property
                  :code #fhir/code "parent"
                  :value #fhir/code "code-180828"}]}])
        [:concepts "code-180828"] := {:fhir/type :fhir.CodeSystem/concept
                                      :code #fhir/code "code-180828"
                                      :property
                                      [{:fhir/type :fhir.CodeSystem.concept/property
                                        :code #fhir/code "child"
                                        :value #fhir/code "code-191445"}]}
        [:concepts "code-191445"] := {:fhir/type :fhir.CodeSystem/concept
                                      :code #fhir/code "code-191445"
                                      :property
                                      [{:fhir/type :fhir.CodeSystem.concept/property
                                        :code #fhir/code "parent"
                                        :value #fhir/code "code-180828"}]}
        :child-index := {"code-180828" #{"code-191445"}})))

  (testing "one root, one child and one child of the child"
    (testing "with hierarchy"
      (given (graph/build-graph
              [{:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-180828"
                :concept
                [{:fhir/type :fhir.CodeSystem/concept
                  :code #fhir/code "code-191445"
                  :concept
                  [{:fhir/type :fhir.CodeSystem/concept
                    :code #fhir/code "code-104304"}]}]}])
        [:concepts "code-180828" :code] := #fhir/code "code-180828"
        [:concepts "code-180828" :property set] := #{{:fhir/type :fhir.CodeSystem.concept/property
                                                      :code #fhir/code "child"
                                                      :value #fhir/code "code-191445"}}
        [:concepts "code-191445" :code] := #fhir/code "code-191445"
        [:concepts "code-191445" :property set] := #{{:fhir/type :fhir.CodeSystem.concept/property
                                                      :code #fhir/code "parent"
                                                      :value #fhir/code "code-180828"}
                                                     {:fhir/type :fhir.CodeSystem.concept/property
                                                      :code #fhir/code "child"
                                                      :value #fhir/code "code-104304"}}
        [:concepts "code-104304" :code] := #fhir/code "code-104304"
        [:concepts "code-104304" :property set] := #{{:fhir/type :fhir.CodeSystem.concept/property
                                                      :code #fhir/code "parent"
                                                      :value #fhir/code "code-191445"}}
        :child-index := {"code-180828" #{"code-191445"}
                         "code-191445" #{"code-104304"}}))

    (testing "with parent property only"
      (given (graph/build-graph
              [{:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-180828"}
               {:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-191445"
                :property
                [{:fhir/type :fhir.CodeSystem.concept/property
                  :code #fhir/code "parent"
                  :value #fhir/code "code-180828"}]}
               {:fhir/type :fhir.CodeSystem/concept
                :code #fhir/code "code-104304"
                :property
                [{:fhir/type :fhir.CodeSystem.concept/property
                  :code #fhir/code "parent"
                  :value #fhir/code "code-191445"}]}])
        [:concepts "code-180828" :code] := #fhir/code "code-180828"
        [:concepts "code-180828" :property set] := #{{:fhir/type :fhir.CodeSystem.concept/property
                                                      :code #fhir/code "child"
                                                      :value #fhir/code "code-191445"}}
        [:concepts "code-191445" :code] := #fhir/code "code-191445"
        [:concepts "code-191445" :property set] := #{{:fhir/type :fhir.CodeSystem.concept/property
                                                      :code #fhir/code "parent"
                                                      :value #fhir/code "code-180828"}
                                                     {:fhir/type :fhir.CodeSystem.concept/property
                                                      :code #fhir/code "child"
                                                      :value #fhir/code "code-104304"}}
        [:concepts "code-104304" :code] := #fhir/code "code-104304"
        [:concepts "code-104304" :property set] := #{{:fhir/type :fhir.CodeSystem.concept/property
                                                      :code #fhir/code "parent"
                                                      :value #fhir/code "code-191445"}}
        :child-index := {"code-180828" #{"code-191445"}
                         "code-191445" #{"code-104304"}})))

  (testing "one child with two parents"
    (given (graph/build-graph
            [{:fhir/type :fhir.CodeSystem/concept
              :code #fhir/code "code-183409"}
             {:fhir/type :fhir.CodeSystem/concept
              :code #fhir/code "code-183411"}
             {:fhir/type :fhir.CodeSystem/concept
              :code #fhir/code "code-191445"
              :property
              [{:fhir/type :fhir.CodeSystem.concept/property
                :code #fhir/code "parent"
                :value #fhir/code "code-183409"}
               {:fhir/type :fhir.CodeSystem.concept/property
                :code #fhir/code "parent"
                :value #fhir/code "code-183411"}]}])
      [:concepts "code-183409"] := {:fhir/type :fhir.CodeSystem/concept
                                    :code #fhir/code "code-183409"
                                    :property
                                    [{:fhir/type :fhir.CodeSystem.concept/property
                                      :code #fhir/code "child"
                                      :value #fhir/code "code-191445"}]}
      [:concepts "code-183411"] := {:fhir/type :fhir.CodeSystem/concept
                                    :code #fhir/code "code-183411"
                                    :property
                                    [{:fhir/type :fhir.CodeSystem.concept/property
                                      :code #fhir/code "child"
                                      :value #fhir/code "code-191445"}]}
      [:concepts "code-191445"] := {:fhir/type :fhir.CodeSystem/concept
                                    :code #fhir/code "code-191445"
                                    :property
                                    [{:fhir/type :fhir.CodeSystem.concept/property
                                      :code #fhir/code "parent"
                                      :value #fhir/code "code-183409"}
                                     {:fhir/type :fhir.CodeSystem.concept/property
                                      :code #fhir/code "parent"
                                      :value #fhir/code "code-183411"}]}
      :child-index := {"code-183409" #{"code-191445"}
                       "code-183411" #{"code-191445"}})))

(deftest is-a-test
  (testing "one root, one child and one child of the child"
    (is (= (-> (graph/build-graph
                [{:fhir/type :fhir.CodeSystem/concept
                  :code #fhir/code "code-180828"}
                 {:fhir/type :fhir.CodeSystem/concept
                  :code #fhir/code "code-191445"
                  :property
                  [{:fhir/type :fhir.CodeSystem.concept/property
                    :code #fhir/code "parent"
                    :value #fhir/code "code-180828"}]}
                 {:fhir/type :fhir.CodeSystem/concept
                  :code #fhir/code "code-104304"
                  :property
                  [{:fhir/type :fhir.CodeSystem.concept/property
                    :code #fhir/code "parent"
                    :value #fhir/code "code-191445"}]}])
               (graph/is-a "code-180828"))
           [{:fhir/type :fhir.CodeSystem/concept
             :code #fhir/code "code-180828"
             :property
             [{:fhir/type :fhir.CodeSystem.concept/property
               :code #fhir/code "child"
               :value #fhir/code "code-191445"}]}
            {:fhir/type :fhir.CodeSystem/concept
             :code #fhir/code "code-104304"
             :property
             [{:fhir/type :fhir.CodeSystem.concept/property
               :code #fhir/code "parent"
               :value #fhir/code "code-191445"}]}
            {:fhir/type :fhir.CodeSystem/concept
             :code #fhir/code "code-191445"
             :property
             [{:fhir/type :fhir.CodeSystem.concept/property
               :code #fhir/code "parent"
               :value #fhir/code "code-180828"}
              {:fhir/type :fhir.CodeSystem.concept/property
               :code #fhir/code "child"
               :value #fhir/code "code-104304"}]}])))

  (testing "one child with two parents"
    (let [parent-1 {:fhir/type :fhir.CodeSystem/concept
                    :code #fhir/code "code-183409"}
          parent-2 {:fhir/type :fhir.CodeSystem/concept
                    :code #fhir/code "code-183411"}
          child {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-183409"}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-183411"}]}]
      (doseq [concepts (tu/permutations [parent-1 parent-2 child])]
        (is (= (-> (graph/build-graph concepts)
                   (graph/is-a "code-183409"))
               [{:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-183409"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "child"
                   :value #fhir/code "code-191445"}]}
                {:fhir/type :fhir.CodeSystem/concept
                 :code #fhir/code "code-191445"
                 :property
                 [{:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-183409"}
                  {:fhir/type :fhir.CodeSystem.concept/property
                   :code #fhir/code "parent"
                   :value #fhir/code "code-183411"}]}]))))))
