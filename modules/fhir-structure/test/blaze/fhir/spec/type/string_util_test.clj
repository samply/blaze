(ns blaze.fhir.spec.type.string-util-test
  (:require
   [blaze.fhir.spec.type.string-util :as su]
   [blaze.fhir.spec.type.string-util-spec]
   [clojure.test :refer [are deftest is]]))

(deftest capital-test
  (are [s c] (= c (su/capital s))
    "" ""
    "ab" "Ab"
    "aB" "AB"
    "Ab" "Ab"
    "AB" "AB"))

(deftest pascal->kebab-test
  (is (= "ab-cd" (su/pascal->kebab "AbCd"))))
