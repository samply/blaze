(ns blaze.interaction.search.include-test
  (:require
    [blaze.db.api :as d]
    [blaze.db.api-stub :refer [mem-node-with]]
    [blaze.fhir.spec.type :as type]
    [blaze.interaction.search.include :as include]
    [blaze.interaction.search.include-spec]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [deftest is testing]]
    [juxt.iota :refer [given]]))


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest add-includes-test
  (testing "one direct include"
    (with-open [node (mem-node-with
                       [[[:put {:fhir/type :fhir/Patient :id "0"}]
                         [:put {:fhir/type :fhir/Observation :id "0"
                                :subject
                                (type/map->Reference
                                  {:reference "Patient/0"})}]]])]
      (let [db (d/db node)
            include-defs {:direct {"Observation" {:code "subject"}}}
            observations (d/type-list db "Observation")]
        (given (into [] (include/add-includes db include-defs observations))
          count := 1
          [0 :match type/type] := :fhir/Observation
          [0 :includes count] := 1
          [0 :includes 0 type/type] := :fhir/Patient)))

    (testing "with non-matching target type"
      (with-open [node (mem-node-with
                         [[[:put {:fhir/type :fhir/Patient :id "0"}]
                           [:put {:fhir/type :fhir/Observation :id "0"
                                  :subject
                                  (type/map->Reference
                                    {:reference "Patient/0"})}]]])]
        (let [db (d/db node)
              include-defs {:direct
                            {"Observation"
                             {:code "subject" :target-type "Group"}}}
              observations (d/type-list db "Observation")]
          (given (into [] (include/add-includes db include-defs observations))
            count := 1
            [0 :match type/type] := :fhir/Observation
            [0 :includes count] := 0))))))


(deftest build-page-test
  (testing "empty input"
    (is (empty? (:matches (include/build-page 1 [])))))

  (testing "one match input"
    (given (include/build-page 1 [{:match :m1}])
      :matches := [:m1]
      :next-match := nil)

    (testing "with one include"
      (given (include/build-page 1 [{:match :m1 :includes [:i1]}])
        :matches := [:m1]
        :includes := #{:i1}
        :next-match := nil))

    (testing "with two includes"
      (given (include/build-page 1 [{:match :m1 :includes [:i1 :i2]}])
        :matches := [:m1]
        :includes := #{:i1 :i2}
        :next-match := nil))

    (given (include/build-page 2 [{:match :m1}])
      :matches := [:m1]
      :next-match := nil))

  (testing "two match inputs"
    (testing "size 1"
      (given (include/build-page 1 [{:match :m1} {:match :m2}])
        :matches := [:m1]
        :next-match := :m2)

      (testing "with one include at the first match"
        (given (include/build-page 1 [{:match :m1 :includes [:i1]} {:match :m2}])
          :matches := [:m1]
          :includes := #{:i1}
          :next-match := :m2)))

    (testing "size 2"
      (given (include/build-page 2 [{:match :m1} {:match :m2}])
        :matches := [:m1 :m2]
        :next-match := nil)

      (testing "with one include at the first match"
        (given (include/build-page 2 [{:match :m1 :includes [:i1]} {:match :m2}])
          :matches := [:m1]
          :includes := #{:i1}
          :next-match := :m2))

      (testing "with two includes at the first match"
        (given (include/build-page 2 [{:match :m1 :includes [:i1 :i2]} {:match :m2}])
          :matches := [:m1]
          :includes := #{:i1 :i2}
          :next-match := :m2)))

    (testing "size 2"
      (given (include/build-page 3 [{:match :m1} {:match :m2}])
        :matches := [:m1 :m2]
        :next-match := nil)

      (testing "with one include at the first match"
        (given (include/build-page 3 [{:match :m1 :includes [:i1]} {:match :m2}])
          :matches := [:m1 :m2]
          :includes := #{:i1}
          :next-match := nil))

      (testing "with two includes at the first match"
        (given (include/build-page 3 [{:match :m1 :includes [:i1 :i2]} {:match :m2}])
          :matches := [:m1]
          :includes := #{:i1 :i2}
          :next-match := :m2)))))
