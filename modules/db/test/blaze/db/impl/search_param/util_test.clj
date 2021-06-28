(ns blaze.db.impl.search-param.util-test
  (:require
    [blaze.db.impl.search-param.util :as util]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is]]
    [taoensso.timbre :as log]))


(st/instrument)
(log/set-level! :trace)


(defn- fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(deftest separate-op-test
  (are [value tuple] (= tuple (util/separate-op value))
    "1" [:eq "1"]
    "eq1" [:eq "1"]
    "ne1" [:ne "1"]
    "fo1" [:eq "fo1"]
    "1ne" [:eq "1ne"]))


(deftest format-skip-indexing-msg-test
  (is (= (util/format-skip-indexing-msg "value-132537" "url-132522" "type-132528")
         "Skip indexing value `value-132537` of type `:fhir/string` for search parameter `url-132522` with type `type-132528` because the rule is missing.")))
