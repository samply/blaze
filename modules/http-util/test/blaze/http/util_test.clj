(ns blaze.http.util-test
  (:require
   [blaze.http.util :as hu]
   [blaze.http.util-spec]
   [blaze.test-util :as tu]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as st]
   [clojure.string :as str]
   [clojure.test :as test :refer [deftest is testing]]
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

(def ^:private cycling-qs
  ["NaN" "0.1" "0.9" "0.5" "0.3"])

(defn- cycling-q [i]
  (cycling-qs (mod (inc i) (count cycling-qs))))

(defn- cycling-q-header
  "Returns a header value with `n` elements named by `name-fn` whose q-values
  cycle through `cycling-qs`.

  With 32 elements, an unordered NaN q-value violates the contract of the sort
  comparator."
  [name-fn n]
  (str/join "," (for [i (range n)] (str (name-fn i) ";q=" (cycling-q i)))))

(defn- cycling-q-order
  "Returns the expected order of the elements of `cycling-q-header` if NaN is
  treated as invalid q-value with the default of 1."
  [name-fn n]
  (for [q ["NaN" "0.9" "0.5" "0.3" "0.1"] i (range n) :when (= q (cycling-q i))]
    (name-fn i)))

(defn- valid-ret? [f-sym ret]
  (s/valid? (:ret (s/get-spec f-sym)) ret))

(deftest parse-header-value-ret-spec-test
  (testing "elements and params without value conform to the :ret spec"
    (doseq [s ["a" "a=b" "a;b" "a;b=c" "a=b;c" "application/fhir+json;q=0.9, return=minimal"]]
      (is (valid-ret? `hu/parse-header-value (hu/parse-header-value s)) s))))

(deftest parse-accept-test
  (testing "nil"
    (is (nil? (hu/parse-accept nil))))

  (testing "empty"
    (is (empty? (hu/parse-accept ""))))

  (testing "single media types including wildcards"
    (doseq [media-type ["application/fhir+json" "*/*" "application/*" "text/*"]]
      (is (= [media-type] (hu/parse-accept media-type)))))

  (testing "media types are converted to lower case"
    (is (= ["application/fhir+json"] (hu/parse-accept "Application/FHIR+JSON"))))

  (testing "sorts by q-value descending"
    (is (= ["application/fhir+json" "application/fhir+xml"]
           (hu/parse-accept "application/fhir+xml;q=0.9, application/fhir+json;q=1.0"))))

  (testing "q-value defaults to 1"
    (is (= ["application/fhir+xml" "application/fhir+json"]
           (hu/parse-accept "application/fhir+json; fhirVersion=4.0; q=0.5, application/fhir+xml"))))

  (testing "keeps the order of equal q-values"
    (is (= ["a/b" "c/d" "e/f"] (hu/parse-accept "a/b, c/d, e/f")))
    (is (= ["application/fhir+xml" "application/fhir+json"]
           (hu/parse-accept "application/fhir+xml;q=0.5, application/fhir+json;q=0.5"))))

  (testing "media types with a q-value of 0 are not acceptable and removed"
    (doseq [q ["0" "0." "0.0" "0.000"]]
      (is (empty? (hu/parse-accept (str "application/fhir+xml;q=" q))) q)
      (is (= ["application/fhir+json"]
             (hu/parse-accept (str "application/fhir+xml;q=" q ", application/fhir+json"))) q)))

  (testing "*/* comes after other media types with the same q-value"
    (is (= ["application/fhir+xml" "*/*"]
           (hu/parse-accept "*/*, application/fhir+xml")))
    (is (= ["application/*" "text/*" "*/*"]
           (hu/parse-accept "*/*;q=0.5, application/*;q=0.5, text/*;q=0.5"))))

  (testing "other parameters are ignored"
    (is (= ["application/fhir+json"]
           (hu/parse-accept "application/fhir+json;fhirVersion=4.0")))
    (is (= ["a/b" "c/d"] (hu/parse-accept "a/b;foo, c/d"))))

  (testing "quoted parameter values can contain commas and semicolons"
    (is (= ["c/d" "a/b"] (hu/parse-accept "a/b;x=\"y, z; w\";q=0.1, c/d"))))

  (testing "q-values outside of [0, 1] are invalid and default to 1"
    (is (= ["a/b" "c/d"] (hu/parse-accept "a/b;q=2, c/d;q=-1")))
    (is (= ["a/b" "c/d"] (hu/parse-accept "a/b;q=1.001, c/d;q=0.5"))))

  (testing "q-values not following the RFC 9110 grammar are invalid and default to 1"
    (doseq [q ["NaN" "Infinity" "0x0.1p0" "0.1d" "1e-1" ".5" "0.1234" "+0.5" " 0.5"]]
      (is (= ["a/b" "c/d"] (hu/parse-accept (str "a/b;q=" q ", c/d;q=0.5"))) q)))

  (testing "valid q-values according to the RFC 9110 grammar"
    (doseq [q ["0.001" "0.12" "0.123"]]
      (is (= ["c/d" "a/b"] (hu/parse-accept (str "a/b;q=" q ", c/d;q=0.5"))) q))
    (doseq [q ["1" "1." "1.0" "1.000"]]
      (is (= ["a/b" "c/d"] (hu/parse-accept (str "c/d;q=0.5, a/b;q=" q))) q)))

  (testing "NaN q-values don't break sorting"
    (is (= (cycling-q-order #(str "x" % "/y") 32)
           (hu/parse-accept (cycling-q-header #(str "x" % "/y") 32)))))

  (testing "the first valid q-value wins"
    (is (= ["c/d" "a/b"] (hu/parse-accept "a/b;q=abc;q=0.3, c/d;q=0.5")))
    (is (= ["c/d" "a/b"] (hu/parse-accept "a/b;q;q=0.3, c/d;q=0.5")))
    (is (= ["c/d" "a/b"] (hu/parse-accept "a/b;q=0.3;q=0.9, c/d;q=0.5"))))

  (testing "the q parameter name is case-insensitive"
    (is (= ["c/d" "a/b"] (hu/parse-accept "a/b;Q=0.1, c/d;q=0.5"))))

  (testing "whitespace around separators is ignored"
    (is (= ["a/b" "c/d"] (hu/parse-accept "  a/b  ,  c/d  ;  q=0.5  ")))
    (is (= ["a/b" "c/d"] (hu/parse-accept "a/b\t, c/d")))
    (is (= ["c/d" "a/b"] (hu/parse-accept "a/b;q=\t0.5, c/d;q=0.6"))))

  (testing "invalid media types are removed"
    (is (empty? (hu/parse-accept "json")))
    (is (empty? (hu/parse-accept "a/b/c")))
    (is (empty? (hu/parse-accept "a/b=c")))
    (is (= ["application/fhir+json"] (hu/parse-accept "foo, application/fhir+json")))
    (is (= ["d/e"] (hu/parse-accept "a/b c, d/e")))
    (is (= ["a/b" "c/d"] (hu/parse-accept "a/b,,c/d"))))

  (testing "fragments without media range are removed"
    (is (empty? (hu/parse-accept ";")))
    (is (= ["a/b"] (hu/parse-accept "a/b, ;q=1")))))

(deftest parse-accept-language-test
  (testing "nil"
    (is (nil? (hu/parse-accept-language nil))))

  (testing "empty"
    (is (empty? (hu/parse-accept-language ""))))

  (testing "single languages including the wildcard"
    (doseq [language ["de" "en-us" "zh-hant-tw" "*"]]
      (is (= [language] (hu/parse-accept-language language)))))

  (testing "languages are converted to lower case"
    (is (= ["de-de"] (hu/parse-accept-language "DE-de"))))

  (testing "sorts by q-value descending"
    (is (= ["en" "de"] (hu/parse-accept-language "de;q=0.5,en")))
    (is (= ["fr" "de" "en"] (hu/parse-accept-language "en;q=0.5, de;q=0.8, fr"))))

  (testing "keeps the order of equal q-values"
    (is (= ["de" "en"] (hu/parse-accept-language "de,en"))))

  (testing "q-values outside of [0, 1] are invalid and default to 1"
    (is (= ["de" "en"] (hu/parse-accept-language "de;q=2,en"))))

  (testing "languages with a q-value of 0 are not acceptable and removed"
    (is (empty? (hu/parse-accept-language "de;q=0")))
    (is (= ["en"] (hu/parse-accept-language "de;q=0,en"))))

  (testing "q-values not following the RFC 9110 grammar are invalid and default to 1"
    (doseq [q ["NaN" "Infinity" "0x0.1p0" "0.1d"]]
      (is (= ["de" "en"] (hu/parse-accept-language (str "de;q=" q ",en;q=0.5"))) q)))

  (testing "NaN q-values don't break sorting"
    (is (= (cycling-q-order #(str "en-" %) 32)
           (hu/parse-accept-language (cycling-q-header #(str "en-" %) 32)))))

  (testing "the first valid q-value wins"
    (is (= ["en" "de"] (hu/parse-accept-language "de;q=abc;q=0.3,en;q=0.9"))))

  (testing "other parameters are ignored"
    (is (= ["en" "de"] (hu/parse-accept-language "de;foo=bar;q=0.1,en"))))

  (testing "whitespace around separators is ignored"
    (is (= ["en" "de"] (hu/parse-accept-language " de ; q = 0.3 , en "))))

  (testing "invalid languages are removed"
    (is (= ["de" "en"] (hu/parse-accept-language ",de,,en")))
    (is (= ["de"] (hu/parse-accept-language "de, ;q=1")))
    (is (empty? (hu/parse-accept-language ";q=0.5")))
    (is (empty? (hu/parse-accept-language "toolongtag")))
    (is (empty? (hu/parse-accept-language "de-")))
    (is (empty? (hu/parse-accept-language "de=en")))
    (is (empty? (hu/parse-accept-language "de en")))))
