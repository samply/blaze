(ns blaze.http.util-test
  (:require
   [blaze.http.util :as hu]
   [blaze.http.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest parse-header-value-test
  (testing "one element without value"
    (given (hu/parse-header-value "a")
      count := 1
      [0 :name] := "a"
      [0 :value] := nil)

    (testing "names are converted to lowercase"
      (given (hu/parse-header-value "A")
        count := 1
        [0 :name] := "a"
        [0 :value] := nil)))

  (testing "one element"
    (given (hu/parse-header-value "a=b")
      count := 1
      [0 :name] := "a"
      [0 :value] := "b"))

  (testing "two elements"
    (given (hu/parse-header-value "a=b,c=d")
      count := 2
      [0 :name] := "a"
      [0 :value] := "b"
      [1 :name] := "c"
      [1 :value] := "d"))

  (testing "one element without value with one param"
    (given (hu/parse-header-value "a;c=d")
      count := 1
      [0 :name] := "a"
      [0 :value] := nil
      [0 :params 0 :name] := "c"
      [0 :params 0 :value] := "d"))

  (testing "one element with one param"
    (given (hu/parse-header-value "a=b;c=d")
      count := 1
      [0 :name] := "a"
      [0 :value] := "b"
      [0 :params 0 :name] := "c"
      [0 :params 0 :value] := "d"))

  (testing "one element with two params"
    (given (hu/parse-header-value "a=b;c=d;e=f")
      count := 1
      [0 :name] := "a"
      [0 :value] := "b"
      [0 :params 0 :name] := "c"
      [0 :params 0 :value] := "d"
      [0 :params 1 :name] := "e"
      [0 :params 1 :value] := "f"))

  (testing "one element with one param and a second element with no param"
    (given (hu/parse-header-value "a=b;c=d,e=f")
      count := 2
      [0 :name] := "a"
      [0 :value] := "b"
      [0 :params 0 :name] := "c"
      [0 :params 0 :value] := "d"
      [1 :name] := "e"
      [1 :value] := "f")))

(deftest form-decode-test
  (testing "empty string"
    (given (hu/form-decode "")
      identity := {}))

  (testing "one param"
    (given (hu/form-decode "a=b")
      identity := {"a" "b"}))

  (testing "two params"
    (given (hu/form-decode "a=b&c=d")
      identity := {"a" "b" "c" "d"}))

  (testing "repeated param"
    (given (hu/form-decode "a=b&a=c&a=d")
      identity := {"a" ["b" "c" "d"]}))

  (testing "param without value"
    (given (hu/form-decode "a")
      identity := {"a" ""}))

  (testing "param with empty value"
    (given (hu/form-decode "a=")
      identity := {"a" ""}))

  (testing "param with empty name"
    (given (hu/form-decode "=b")
      identity := {"" "b"}))

  (testing "value containing an equals sign"
    (given (hu/form-decode "a=b=c")
      identity := {"a" "b=c"}))

  (testing "empty params are skipped"
    (given (hu/form-decode "&a=b&&c=d&")
      identity := {"a" "b" "c" "d"}))

  (testing "percent-encoded name and value"
    (given (hu/form-decode "a%3Ab=c%2Cd%20e")
      identity := {"a:b" "c,d e"}))

  (testing "lower-case hex digits"
    (given (hu/form-decode "a=%c3%a4")
      identity := {"a" "ä"}))

  (testing "plus sign is decoded as space"
    (given (hu/form-decode "a+b=c+d")
      identity := {"a b" "c d"}))

  (testing "with encoding"
    (given (hu/form-decode "a=%E4" "ISO-8859-1")
      identity := {"a" "ä"}))

  (testing "invalid percent-encoding"
    (doseq [s ["%ZZ=b" "a=%ZZ" "a=%" "a=%2" "a=b%" "a=%-1" "a=%+1" "a=%2G"]]
      (testing s
        (given (hu/form-decode s)
          ::anom/category := ::anom/incorrect
          ::anom/message := (format "Invalid percent-encoding in parameter `%s`." s)
          :fhir/issue := "invalid")))

    (testing "after a valid param"
      (given (hu/form-decode "a=b&identifier=%ZZ")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid percent-encoding in parameter `identifier=%ZZ`."))

    (testing "the first invalid param is reported"
      (given (hu/form-decode "a=%ZZ&b=%YY")
        ::anom/category := ::anom/incorrect
        ::anom/message := "Invalid percent-encoding in parameter `a=%ZZ`."))))
