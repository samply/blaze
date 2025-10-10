(ns blaze.interaction.util-test
  (:require
   [blaze.db.api :as d]
   [blaze.db.api-stub :refer [mem-node-config with-system-data]]
   [blaze.db.tx-log.spec]
   [blaze.fhir.hash :as hash]
   [blaze.interaction.util :as iu]
   [blaze.interaction.util-spec]
   [blaze.module.test-util :refer [with-system]]
   [blaze.test-util :as tu :refer [satisfies-prop]]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [are deftest is testing]]
   [clojure.test.check.generators :as gen]
   [clojure.test.check.properties :as prop]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest etag->t-test
  (testing "valid ETag"
    (is (= 1 (iu/etag->t "W/\"1\""))))

  (testing "invalid ETag"
    (are [s] (nil? (iu/etag->t s))
      "foo"
      "W/1"
      "W/\"a\"")))

(deftest put-tx-op-test
  (testing "on empty database"
    (with-system [{:blaze.db/keys [node]} mem-node-config]
      (testing "with empty if-match header"
        (given (iu/update-tx-op (d/db node) {:fhir/type :fhir/Patient :id "0"} "" nil)
          ::anom/category := ::anom/conflict
          ::anom/message := "Empty precondition failed on `Patient/0`."
          :http/status := 412))

      (testing "with invalid if-match header"
        (satisfies-prop 1000
          (prop/for-all [if-match (gen/such-that (complement str/blank?) gen/string)]
            (let [anom (iu/update-tx-op (d/db node) {:fhir/type :fhir/Patient :id "0"} if-match nil)]
              (and (= ::anom/conflict (::anom/category anom))
                   (= (format "Precondition `%s` failed on `Patient/0`." if-match) (::anom/message anom))
                   (= 412 (:http/status anom)))))))

      (testing "without preconditions"
        (is (= (iu/update-tx-op (d/db node) {:fhir/type :fhir/Patient :id "0"} nil nil)
               [:put {:fhir/type :fhir/Patient :id "0"}])))

      (testing "with some precondition"
        (satisfies-prop 10
          (prop/for-all [ts (gen/vector (gen/choose 1 10) 1 3)]
            (is (= (iu/update-tx-op (d/db node) {:fhir/type :fhir/Patient :id "0"} (str/join "," (map (partial format "W/\"%d\"") ts)) nil)
                   [:put {:fhir/type :fhir/Patient :id "0"} (into [:if-match] ts)])))))))

  (testing "with an existing, identical patient; the other patient is there in order to show that the t depends only on the matching patient"
    (let [male-patient {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "male"}
          hash (hash/generate male-patient)]
      (with-system-data [{:blaze.db/keys [node]} mem-node-config]
        [[[:put {:fhir/type :fhir/Patient :id "0" :gender #fhir/code "female"}]]
         [[:put male-patient]]
         [[:put {:fhir/type :fhir/Patient :id "1"}]]]

        (testing "without preconditions"
          (testing "generates a keep op with the hash of the current patient version"
            (is (= (iu/update-tx-op (d/db node) male-patient nil nil)
                   [:keep "Patient" "0" hash]))))

        (testing "with one matching if-match"
          (testing "generates a keep op with the hash of the current patient version and the t of the if-match header"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"2\"" nil)
                   [:keep "Patient" "0" hash [2]]))))

        (testing "with newer if-match"
          (testing "generates a put op because the newer t could still match inside the transaction"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"3\"" nil)
                   [:put male-patient [:if-match 3]]))))

        (testing "with older if-match"
          (testing "returns an conflict, because the older t will never match again"
            (given (iu/update-tx-op (d/db node) male-patient "W/\"1\"" nil)
              ::anom/category := ::anom/conflict
              ::anom/message := "Precondition `W/\"1\"` failed on `Patient/0`."
              :http/status := 412)))

        (testing "with the same and older if-match"
          (testing "generates a keep op with the hash of the current patient version"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"1\",W/\"2\"" nil)
                   [:keep "Patient" "0" hash [2]]))))

        (testing "with the same and newer if-match"
          (testing "generates a keep op with the hash of the current patient version"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"2\",W/\"3\"" nil)
                   [:keep "Patient" "0" hash [2 3]]))))

        (testing "with newer and older if-match"
          (testing "generates a put op because the newer t could still match inside the transaction and removes the older t that can't match anymore"
            (is (= (iu/update-tx-op (d/db node) male-patient "W/\"1\",W/\"3\"" nil)
                   [:put male-patient [:if-match 3]]))))))))
