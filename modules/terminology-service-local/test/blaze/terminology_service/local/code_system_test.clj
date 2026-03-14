(ns blaze.terminology-service.local.code-system-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :as api-stub :refer [with-system-data]]
   [blaze.terminology-service.local.code-system :as cs]
   [blaze.terminology-service.local.code-system-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest list-test
  (testing "with one code system"
    (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
      [[[:put {:fhir/type :fhir/CodeSystem :id "id-160617"
               :url #fhir/uri "system-192435"
               :content #fhir/code "complete"}]]]

      (given @(cs/list (d/db node))
        count := 1
        [0 key] := "system-192435"
        [0 val count] := 1
        [0 val 0 :id] := "id-160617")))

  (testing "with two code system"
    (testing "with same URL"
      (testing "which are identical"
        (testing "created in the same transaction"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :content #fhir/code "complete"}]
              [:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :content #fhir/code "complete"}]]]

            (testing "the one with the higher id comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-1"
                [0 val 1 :id] := "id-0"))))

        (testing "created in different transactions"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :content #fhir/code "complete"}]]
             [[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :content #fhir/code "complete"}]]]

            (testing "the newer one comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-0"
                [0 val 1 :id] := "id-1")))))

      (testing "with different versions"
        (testing "major only"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "2"
                     :content #fhir/code "complete"}]]
             [[:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1"
                     :content #fhir/code "complete"}]]]

            (testing "the higher version comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-0"
                [0 val 1 :id] := "id-1"))))

        (testing "same major but different numeric minor"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.10"
                     :content #fhir/code "complete"}]]
             [[:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.2"
                     :content #fhir/code "complete"}]]]

            (testing "the higher version comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-0"
                [0 val 1 :id] := "id-1"))))

        (testing "same major but different mixed minor"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.a"
                     :content #fhir/code "complete"}]]
             [[:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.2"
                     :content #fhir/code "complete"}]]]

            (testing "the alpha version comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-0"
                [0 val 1 :id] := "id-1"))))

        (testing "major and major.minor"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "2"
                     :content #fhir/code "complete"}]]
             [[:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.2"
                     :content #fhir/code "complete"}]]]

            (testing "the alpha version comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-0"
                [0 val 1 :id] := "id-1"))))

        (testing "active version comes before no version"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :status #fhir/code "active"
                     :version #fhir/string "1.1"
                     :content #fhir/code "complete"}]]
             [[:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.2"
                     :content #fhir/code "complete"}]]]

            (testing "the alpha version comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-0"
                [0 val 1 :id] := "id-1"))))

        (testing "active version comes before draft version"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.1"
                     :status #fhir/code "active"
                     :content #fhir/code "complete"}]]
             [[:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.2"
                     :status #fhir/code "draft"
                     :content #fhir/code "complete"}]]]

            (testing "the alpha version comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-0"
                [0 val 1 :id] := "id-1"))))

        (testing "draft version comes before retired version"
          (with-system-data [{:blaze.db/keys [node]} api-stub/mem-node-config]
            [[[:put {:fhir/type :fhir/CodeSystem :id "id-0"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.1"
                     :status #fhir/code "draft"
                     :content #fhir/code "complete"}]]
             [[:put {:fhir/type :fhir/CodeSystem :id "id-1"
                     :url #fhir/uri "system-192435"
                     :version #fhir/string "1.2"
                     :status #fhir/code "retired"
                     :content #fhir/code "complete"}]]]

            (testing "the alpha version comes first"
              (given @(cs/list (d/db node))
                count := 1
                [0 key] := "system-192435"
                [0 val count] := 2
                [0 val 0 :id] := "id-0"
                [0 val 1 :id] := "id-1"))))))))
