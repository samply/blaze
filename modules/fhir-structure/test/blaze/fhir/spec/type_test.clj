(ns blaze.fhir.spec.type-test
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [cheshire.core :as json]
    [cheshire.parse :refer [*use-bigdecimals?*]]
    [clojure.data.xml.name :as xml-name]
    [clojure.data.xml.node :as xml-node]
    [clojure.data.xml.prxml :as prxml]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cuerdas.core :as str])
  (:import
    [java.time Instant LocalDate LocalTime OffsetDateTime Year YearMonth
               ZoneOffset]
    [org.openjdk.jol.info ClassLayout GraphLayout]
    [com.google.common.hash Hashing]))


(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")


(st/instrument)


(defn fixture [f]
  (st/instrument)
  (f)
  (st/unstrument))


(test/use-fixtures :each fixture)


(defn class-layout [c]
  (.toPrintable (ClassLayout/parseClass c)))


(defn instance-size [c]
  (.instanceSize (ClassLayout/parseClass c)))


(defn graph-layout [& xs]
  (.toPrintable (GraphLayout/parseInstance (object-array xs))))


(defn total-size [& xs]
  (.totalSize (GraphLayout/parseInstance (object-array xs))))


(defn parse-json [s]
  (binding [*use-bigdecimals?* true] (json/parse-string s keyword)))


(defn murmur3 [x]
  (let [hasher (.newHasher (Hashing/murmur3_32))]
    (type/hash-into x hasher)
    (Integer/toHexString (.asInt (.hash hasher)))))


(def sexp prxml/sexp-as-element)


(def string-extension
  {:fhir/type :fhir/Extension
   :url #fhir/uri"foo"
   :valueString "bar"})


(deftest nil-test
  (testing "all FhirType methods can be called on nil"
    (testing "type"
      (is (nil? (type/type nil))))
    (testing "value"
      (is (nil? (type/value nil))))
    (testing "to-json"
      (is (nil? (type/to-json nil))))
    (testing "to-xml"
      (is (nil? (type/to-xml nil))))
    (testing "hash-into"
      (is (= "0" (murmur3 nil))))))


(deftest Object-test
  (testing "arbitrary instances have no fhir type"
    (is (nil? (type/type (Object.))))))


(deftest boolean-test
  (testing "type"
    (is (= :fhir/boolean (type/type true))))
  (testing "value"
    (is (= true (type/value true))))
  (testing "to-json"
    (is (= true (type/to-json true))))
  (testing "to-xml"
    (are [b s] (= (sexp [nil {:value s}]) (type/to-xml b))
      true "true"
      false "false"))
  (testing "hash-into"
    (are [b hex] (= hex (murmur3 b))
      true "90690515"
      false "70fda443")))


(deftest integer-test
  (testing "from XML"
    (is (= #fhir/integer 1 (type/xml->Integer (sexp [nil {:value "1"}])))))
  (testing "type"
    (is (= :fhir/integer (type/type #fhir/integer 1))))
  (testing "value"
    (is (= 1 (type/value #fhir/integer 1))))
  (testing "to-json"
    (is (= 1 (type/to-json #fhir/integer 1))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "1"}]) (type/to-xml #fhir/integer 1))))
  (testing "instance size"
    (is (= 16 (total-size #fhir/integer 1))))
  (testing "hash-into"
    (are [i hex] (= hex (murmur3 i))
      (int 0) "ab61a435"
      (int 1) "f9ff6b7c")))


(deftest long-test
  (testing "from XML"
    (is (= #fhir/integer 1 (type/xml->Long (sexp [nil {:value "1"}])))))
  (testing "type"
    (is (= :fhir/long (type/type #fhir/long 1))))
  (testing "value"
    (is (= 1 (type/value #fhir/long 1))))
  (testing "to-json"
    (is (= 1 (type/to-json #fhir/long 1))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "1"}]) (type/to-xml #fhir/long 1))))
  (testing "instance size"
    (is (= 24 (total-size #fhir/long 1))))
  (testing "hash-into"
    (are [i hex] (= hex (murmur3 i))
      0 "9bc977cc"
      1 "fac0175c")))


(deftest string-test
  (testing "from XML"
    (is (= "142214" (type/xml->String (sexp [nil {:value "142214"}])))))
  (testing "type"
    (is (= :fhir/string (type/type ""))))
  (testing "value"
    (is (= "175227" (type/value "175227"))))
  (testing "to-json"
    (is (= "105406" (type/to-json "105406"))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "121344"}]) (type/to-xml "121344"))))
  (testing "instance size"
    (is (= 48 (total-size "a")))
    (is (= 48 (total-size (str/repeat "a" 8))))
    (is (= 56 (total-size (str/repeat "a" 9)))))
  (testing "hash-into"
    (are [s hex] (= hex (murmur3 s))
      "" "126916b"
      "foo" "ba7851a6")))


(deftest decimal-test
  (testing "from XML"
    (is (= 1M (type/xml->Decimal (sexp [nil {:value "1"}])))))
  (testing "type"
    (is (= :fhir/decimal (type/type 1M))))
  (testing "value"
    (is (= 1M (type/value 1M))))
  (testing "to-json"
    (is (= 1M (type/to-json 1M))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "1.1"}]) (type/to-xml 1.1M))))
  (testing "instance size"
    (is (= 40 (total-size (parse-json "1.1")))))
  (testing "hash-into"
    (are [d hex] (= hex (murmur3 d))
      0M "7e564b82"
      1M "f2f4ddc7")))


(deftest uri-test
  (testing "from XML"
    (is (= #fhir/uri"142307" (type/xml->Uri (sexp [nil {:value "142307"}])))))
  (testing "type"
    (is (= :fhir/uri (type/type #fhir/uri""))))
  (testing "value"
    (is (= "105614" (type/value #fhir/uri"105614"))))
  (testing "to-json"
    (is (= "105846" (type/to-json #fhir/uri"105846"))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "105846"}]) (type/to-xml #fhir/uri"105846"))))
  (testing "equals"
    (is (= #fhir/uri"142334" #fhir/uri"142334")))
  (testing "print"
    (is (= "#fhir/uri\"142600\"" (pr-str #fhir/uri"142600"))))
  (testing "instance size"
    (is (= 56 (total-size #fhir/uri"")))
    (is (= 64 (total-size #fhir/uri"a"))))
  (testing "hash-into"
    (are [u hex] (= hex (murmur3 u))
      #fhir/uri"" "51a99a01"
      #fhir/uri"foo" "dc60f982")))


(deftest url-test
  (testing "from XML"
    (is (= #fhir/url"142307" (type/xml->Url (sexp [nil {:value "142307"}])))))
  (testing "type"
    (is (= :fhir/url (type/type #fhir/url""))))
  (testing "value"
    (is (= "105614" (type/value #fhir/url"105614"))))
  (testing "to-json"
    (is (= "105846" (type/to-json #fhir/url"105846"))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "105846"}]) (type/to-xml #fhir/url"105846"))))
  (testing "equals"
    (is (= #fhir/url"142334" #fhir/url"142334")))
  (testing "print"
    (is (= "#fhir/url\"142600\"" (pr-str #fhir/url"142600"))))
  (testing "instance size"
    (is (= 56 (total-size #fhir/url"")))
    (is (= 64 (total-size #fhir/url"a"))))
  (testing "hash-into"
    (are [u hex] (= hex (murmur3 u))
      #fhir/url"" "39b10d82"
      #fhir/url"foo" "7acc4e54")))


(deftest canonical-test
  (testing "from XML"
    (is (= #fhir/canonical"142307" (type/xml->Canonical (sexp [nil {:value "142307"}])))))
  (testing "type"
    (is (= :fhir/canonical (type/type #fhir/canonical""))))
  (testing "value"
    (is (= "105614" (type/value #fhir/canonical"105614"))))
  (testing "to-json"
    (is (= "105846" (type/to-json #fhir/canonical"105846"))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "105846"}]) (type/to-xml #fhir/canonical"105846"))))
  (testing "equals"
    (is (= #fhir/canonical"142334" #fhir/canonical"142334")))
  (testing "print"
    (is (= "#fhir/canonical\"142600\"" (pr-str #fhir/canonical"142600"))))
  (testing "instance size"
    (is (= 56 (total-size #fhir/canonical"")))
    (is (= 64 (total-size #fhir/canonical"a"))))
  (testing "hash-into"
    (are [u hex] (= hex (murmur3 u))
      #fhir/canonical"" "53c915fd"
      #fhir/canonical"foo" "e42e9c7e")))


(deftest base64Binary-test
  (testing "from XML"
    (is (= #fhir/base64Binary"MTA1NjE0Cg=="
           (type/xml->Base64Binary (sexp [nil {:value "MTA1NjE0Cg=="}])))))
  (testing "type"
    (is (= :fhir/base64Binary (type/type #fhir/base64Binary""))))
  (testing "value"
    (is (= "MTA1NjE0Cg==" (type/value #fhir/base64Binary"MTA1NjE0Cg=="))))
  (testing "to-json"
    (is (= "MTA1NjE0Cg==" (type/to-json #fhir/base64Binary"MTA1NjE0Cg=="))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "MTA1NjE0Cg=="}]) (type/to-xml #fhir/base64Binary"MTA1NjE0Cg=="))))
  (testing "equals"
    (is (= #fhir/base64Binary"MTA1NjE0Cg==" #fhir/base64Binary"MTA1NjE0Cg==")))
  (testing "instance size"
    (is (= 56 (total-size #fhir/base64Binary"")))
    (is (= 64 (total-size #fhir/base64Binary"YQo=")))
    (is (= 72 (total-size #fhir/base64Binary"MTA1NjE0Cg=="))))
  (testing "hash-into"
    (are [u hex] (= hex (murmur3 u))
      #fhir/base64Binary"" "339ff20a"
      #fhir/base64Binary"YQo=" "ed565602"
      #fhir/base64Binary"MTA1NjE0Cg===" "24568b10")))


(deftest instant-test
  (testing "from XML"
    (is (= Instant/EPOCH
           (type/xml->Instant (sexp [nil {:value "1970-01-01T00:00:00Z"}])))))
  (testing "from JSON"
    (is (= Instant/EPOCH (type/->Instant "1970-01-01T00:00:00Z"))))
  (testing "type"
    (is (= :fhir/instant
           (type/type (type/->Instant "2020-01-01T00:00:00+02:00"))))
    (is (= :fhir/instant (type/type Instant/EPOCH))))
  (testing "value is a System.DateTime which is a OffsetDateTime"
    (is (= (OffsetDateTime/of 2020 1 1 0 0 0 0 (ZoneOffset/ofHours 2))
           (type/value (type/->Instant "2020-01-01T00:00:00+02:00"))))
    (is (= (OffsetDateTime/of 1970 1 1 0 0 0 0 ZoneOffset/UTC)
           (type/value Instant/EPOCH))))
  (testing "to-json"
    (is (= "2020-01-01T00:00:00+02:00"
           (type/to-json (type/->Instant "2020-01-01T00:00:00+02:00"))))
    (is (= "1970-01-01T00:00:00Z" (type/to-json Instant/EPOCH))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "2020-01-01T00:00:00+02:00"}])
           (type/to-xml (type/->Instant "2020-01-01T00:00:00+02:00"))))
    (is (= (sexp  [nil {:value "1970-01-01T00:00:00Z"}])
           (type/to-xml Instant/EPOCH))))
  (testing "equals"
    (is (= (type/->Instant "2020-01-01T00:00:00+02:00")
           (type/->Instant "2020-01-01T00:00:00+02:00")))
    (is (= Instant/EPOCH (type/->Instant "1970-01-01T00:00:00Z")))
    (is (= Instant/EPOCH (type/->Instant "1970-01-01T00:00:00+00:00"))))
  (testing "instance size"
    (testing "backed by OffsetDateTime, taking into account shared offsets"
      (is (= 112 (- (total-size (type/->Instant "2020-01-01T00:00:00+02:00"))
                    (total-size ZoneOffset/UTC)))))
    (testing "backed by java.time.Instant"
      (is (= 24 (total-size Instant/EPOCH)))))
  (testing "hash-into"
    (are [u hex] (= hex (murmur3 u))
      (type/->Instant "2020-01-01T00:00:00Z") "d81f6bc2"
      (type/->Instant "1970-01-01T00:00:00Z") "93344244"
      Instant/EPOCH "93344244")))


(deftest date-test
  (testing "with year precision"
    (testing "from XML"
      (is (= #fhir/date"2010" (type/xml->Date (sexp [nil {:value "2010"}])))))
    (testing "type"
      (is (= :fhir/date (type/type #fhir/date"2020"))))
    (testing "value"
      (is (= (Year/of 2020) (type/value #fhir/date"2020"))))
    (testing "to-json"
      (is (= "2020" (type/to-json #fhir/date"2020"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020"}]) (type/to-xml #fhir/date"2020"))))
    (testing "equals"
      (is (= #fhir/date"2020" #fhir/date"2020")))
    (testing "instance size"
      (is (= 16 (total-size #fhir/date"2020"))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/date"2020" "c92be432")))

  (testing "with year-month precision"
    (testing "from XML"
      (is (= #fhir/date"2010-04"
             (type/xml->Date (sexp [nil {:value "2010-04"}])))))
    (testing "type"
      (is (= :fhir/date (type/type #fhir/date"2020-01"))))
    (testing "value"
      (is (= (YearMonth/of 2020 1) (type/value #fhir/date"2020-01"))))
    (testing "to-json"
      (is (= "2020-01" (type/to-json #fhir/date"2020-01"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01"}]) (type/to-xml #fhir/date"2020-01"))))
    (testing "equals"
      (is (= #fhir/date"2020-01" #fhir/date"2020-01")))
    (testing "instance size"
      (is (= 24 (total-size #fhir/date"2020-01"))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/date"2020-01" "fbcdf97f")))

  (testing "with date precision"
    (testing "from XML"
      (is (= #fhir/date"2010-05-15"
             (type/xml->Date (sexp [nil {:value "2010-05-15"}])))))
    (testing "type"
      (is (= :fhir/date (type/type #fhir/date"2020-01-01"))))
    (testing "value"
      (is (= (LocalDate/of 2020 1 1) (type/value #fhir/date"2020-01-01"))))
    (testing "to-json"
      (is (= "2020-01-01" (type/to-json #fhir/date"2020-01-01"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01-01"}])
             (type/to-xml #fhir/date"2020-01-01"))))
    (testing "equals"
      (is (= #fhir/date"2020-01-01" #fhir/date"2020-01-01")))
    (testing "instance size"
      (is (= 24 (total-size #fhir/date"2020-01-01"))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/date"2020-01-01" "cd20e081"))))


(deftest dateTime-test
  (testing "with year precision"
    (testing "from XML"
      (is (= #fhir/dateTime"2010"
             (type/xml->DateTime (sexp [nil {:value "2010"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020"))))
    (testing "value"
      (is (= (system/date-time 2020) (type/value #fhir/dateTime"2020"))))
    (testing "to-json"
      (is (= "2020" (type/to-json #fhir/dateTime"2020"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020"}]) (type/to-xml #fhir/dateTime"2020"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020" #fhir/dateTime"2020")))
    (testing "instance size"
      (is (= 32 (total-size #fhir/dateTime"2020"))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020" "41e906ff"))
    (comment
      (quick-bench (type/->DateTime "2020"))))

  (testing "with year-month precision"
    (testing "from XML"
      (is (= #fhir/dateTime"2010-04"
             (type/xml->DateTime (sexp [nil {:value "2010-04"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01"))))
    (testing "value"
      (is (= (system/date-time 2020 1)
             (type/value #fhir/dateTime"2020-01"))))
    (testing "to-json"
      (is (= "2020-01" (type/to-json #fhir/dateTime"2020-01"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01"}])
             (type/to-xml #fhir/dateTime"2020-01"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01" #fhir/dateTime"2020-01")))
    (testing "instance size"
      (is (= 40 (total-size #fhir/dateTime"2020-01"))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020-01" "9d6c5bd3")))

  (testing "with date precision"
    (testing "from XML"
      (is (= #fhir/dateTime"2010-05-15"
             (type/xml->DateTime (sexp [nil {:value "2010-05-15"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01-01"))))
    (testing "value"
      (is (= (system/date-time 2020 1 1)
             (type/value #fhir/dateTime"2020-01-01"))))
    (testing "to-json"
      (is (= "2020-01-01" (type/to-json #fhir/dateTime"2020-01-01"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01-01"}])
             (type/to-xml #fhir/dateTime"2020-01-01"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01" #fhir/dateTime"2020-01-01")))
    (testing "instance size"
      (is (= 40 (total-size #fhir/dateTime"2020-01-01"))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020-01-01" "39fe9bdb"))
    (comment
      (quick-bench (type/->DateTime "2020-01-01"))))

  (testing "without timezone"
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01-01T00:00:00"))))
    (testing "to-json"
      (is (= "2020-01-01T00:00:00"
             (type/to-json #fhir/dateTime"2020-01-01T00:00:00"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01-01T00:00:00"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00"
             #fhir/dateTime"2020-01-01T00:00:00")))
    (testing "instance size"
      (is (= 72 (total-size #fhir/dateTime"2020-01-01T00:00:00"))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020-01-01T00:00:00" "da537591")))

  (testing "without timezone but millis"
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00.001"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00.001"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01-01T00:00:00.000"))))
    (testing "to-json"
      (is (= "2020-01-01T00:00:00.001"
             (type/to-json #fhir/dateTime"2020-01-01T00:00:00.001"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01-01T00:00:00.001"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00.001"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00.000"
             #fhir/dateTime"2020-01-01T00:00:00.000")))
    (testing "instance size"
      (is (= 72 (total-size #fhir/dateTime"2020-01-01T00:00:00.000"))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020-01-01T00:00:00.000" "da537591")))

  (testing "with zulu timezone"
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00Z"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00Z"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01-01T00:00:00Z"))))
    (testing "to-json"
      (is (= "2020-01-01T00:00:00Z"
             (type/to-json #fhir/dateTime"2020-01-01T00:00:00Z"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01-01T00:00:00Z"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00Z"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00Z"
             #fhir/dateTime"2020-01-01T00:00:00Z")))
    (testing "instance size taking into account shared offsets"
      (is (= 96 (- (total-size #fhir/dateTime"2020-01-01T00:00:00Z")
                   (total-size ZoneOffset/UTC)))))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020-01-01T00:00:00Z" "d541a45")))

  (testing "with positive timezone offset"
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00+01:00"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00+01:00"}])))))
    (testing "type"
      (is (= :fhir/dateTime
             (type/type #fhir/dateTime"2020-01-01T00:00:00+01:00"))))
    (testing "to-json"
      (is (= "2020-01-01T00:00:00+01:00"
             (type/to-json #fhir/dateTime"2020-01-01T00:00:00+01:00"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01-01T00:00:00+01:00"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00+01:00"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00+01:00"
             #fhir/dateTime"2020-01-01T00:00:00+01:00")))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020-01-01T00:00:00+01:00" "9c535d0d")))

  (testing "with negative timezone offset"
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00-01:00"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00-01:00"}])))))
    (testing "type"
      (is (= :fhir/dateTime
             (type/type #fhir/dateTime"2020-01-01T00:00:00-01:00"))))
    (testing "to-json"
      (is (= "2020-01-01T00:00:00-01:00"
             (type/to-json #fhir/dateTime"2020-01-01T00:00:00-01:00"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01-01T00:00:00-01:00"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00-01:00"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00-01:00"
             #fhir/dateTime"2020-01-01T00:00:00-01:00")))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020-01-01T00:00:00-01:00" "839fd8a6")))

  (testing "with zulu timezone and millis"
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00.001Z"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00.001Z"}])))))
    (testing "type"
      (is (= :fhir/dateTime
             (type/type #fhir/dateTime"2020-01-01T00:00:00.001Z"))))
    (testing "value is a System.DateTime which is a OffsetDateTime"
      (is (= (OffsetDateTime/of 2020 1 1 0 0 0 1000000 ZoneOffset/UTC)
             (type/value #fhir/dateTime"2020-01-01T00:00:00.001Z"))))
    (testing "to-json"
      (is (= "2020-01-01T00:00:00.001Z"
             (type/to-json #fhir/dateTime"2020-01-01T00:00:00.001Z"))))
    (testing "to-xml"
      (is (= (sexp  [nil {:value "2020-01-01T00:00:00.001Z"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00.001Z"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00.001Z"
             #fhir/dateTime"2020-01-01T00:00:00.001Z")))
    (testing "hash-into"
      (are [u hex] (= hex (murmur3 u))
        #fhir/dateTime"2020-01-01T00:00:00.001Z" "f46a0b1b")))

  (testing "with extensions"
    (let [extended-date-time (type/->DateTime nil [string-extension] "2020")
          extended-date-time-element (xml-node/element nil {:value "2020"} string-extension)]
      (testing "type"
        (is (= :fhir/dateTime (type/type extended-date-time))))
      (testing "value"
        (is (= (system/date-time 2020) (type/value extended-date-time))))
      (testing "to-json"
        (is (= "2020" (type/to-json extended-date-time))))
      (testing "to-xml"
        (is (= extended-date-time-element (type/to-xml extended-date-time))))
      (testing "equals"
        (is (= (type/->DateTime nil [string-extension] "2020") extended-date-time)))
      (testing "hash-into"
        (are [u hex] (= hex (murmur3 u))
          extended-date-time "e3246eac"))
      (comment
        (quick-bench extended-date-time)))))


(deftest time-test
  (testing "from XML"
    (is (= #fhir/time"13:53:21" (type/xml->Time (sexp [nil {:value "13:53:21"}])))))
  (testing "type"
    (is (= :fhir/time (type/type #fhir/time"13:53:21"))))
  (testing "value is a System.Time which is a LocalTime"
    (is (= (LocalTime/of 13 53 21) (type/value #fhir/time"13:53:21"))))
  (testing "to-json"
    (is (= "13:53:21" (type/to-json #fhir/time"13:53:21"))))
  (testing "to-xml"
    (is (= (sexp  [nil {:value "13:53:21"}])
           (type/to-xml #fhir/time"13:53:21"))))
  (testing "equals"
    (is (= #fhir/time"13:53:21" #fhir/time"13:53:21")))
  (testing "hash-into"
    (are [u hex] (= hex (murmur3 u))
      #fhir/time"13:53:21" "faa37be9"))
  (testing "instance size"
    (is (= 24 (total-size #fhir/time"13:53:21")))))


(def gender-extension
  {:fhir/type :fhir/Extension
   :url #fhir/uri"http://fhir.de/StructureDefinition/gender-amtlich-de"
   :value
   {:fhir/type :fhir/Coding
    :system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
    :code #fhir/code"D"
    :display "divers"}})


(def extended-gender-code
  (type/->ExtendedCode nil [gender-extension] "other"))


(def extended-gender-code-element
  (xml-node/element nil {:value "other"} gender-extension))


(deftest code-test
  (testing "from XML"
    (is (= #fhir/code"code-150725"
           (type/xml->Code (sexp [nil {:value "code-150725"}])))))
  (testing "type"
    (is (= :fhir/code (type/type #fhir/code""))))
  (testing "value is a System.String which is a String"
    (is (= "code-123745" (type/value #fhir/code"code-123745")))
    (is (= "other" (type/value (type/->ExtendedCode nil [] "other")))))
  (testing "to-json"
    (is (= "code-123745" (type/to-json #fhir/code"code-123745"))))
  (testing "to-xml"
    (is (= (sexp [nil {:value "code-123745"}])
           (type/to-xml #fhir/code"code-123745")))
    (is (= extended-gender-code-element (type/to-xml extended-gender-code))))
  (testing "equals"
    (is (= #fhir/code"175726" #fhir/code"175726")))
  (testing "print"
    (is (= "#fhir/code\"175718\"" (pr-str #fhir/code"175718"))))
  (testing "instance size"
    (is (= 56 (total-size #fhir/code"")))
    (is (= 64 (total-size #fhir/code"175718")))))


(deftest oid-test
  (testing "from XML"
    (is (= #fhir/oid"oid-150725"
           (type/xml->Oid (sexp [nil {:value "oid-150725"}])))))
  (testing "type"
    (is (= :fhir/oid (type/type #fhir/oid""))))
  (testing "value is a System.String which is a String"
    (is (= "oid-123745" (type/value #fhir/oid"oid-123745"))))
  (testing "to-json"
    (is (= "oid-123745" (type/to-json #fhir/oid"oid-123745"))))
  (testing "to-xml"
    (is (= (sexp [nil {:value "oid-123745"}])
           (type/to-xml #fhir/oid"oid-123745"))))
  (testing "equals"
    (is (= #fhir/oid"175726" #fhir/oid"175726")))
  (testing "print"
    (is (= "#fhir/oid\"175718\"" (pr-str #fhir/oid"175718"))))
  (testing "instance size"
    (testing "instance size"
      (is (= 56 (total-size #fhir/oid"")))
      (is (= 64 (total-size #fhir/oid"175718"))))))


(deftest id-test
  (testing "from XML"
    (is (= #fhir/id"id-150725"
           (type/xml->Id (sexp [nil {:value "id-150725"}])))))
  (testing "type"
    (is (= :fhir/id (type/type #fhir/id""))))
  (testing "value is a System.String which is a String"
    (is (= "id-123745" (type/value #fhir/id"id-123745"))))
  (testing "to-json"
    (is (= "id-123745" (type/to-json #fhir/id"id-123745"))))
  (testing "to-xml"
    (is (= (sexp [nil {:value "id-123745"}])
           (type/to-xml #fhir/id"id-123745"))))
  (testing "equals"
    (is (= #fhir/id"175726" #fhir/id"175726")))
  (testing "print"
    (is (= "#fhir/id\"175718\"" (pr-str #fhir/id"175718"))))
  (testing "instance size"
    (is (= 56 (total-size #fhir/id"")))
    (is (= 64 (total-size #fhir/id"175718")))))


(deftest markdown-test
  (testing "from XML"
    (is (= #fhir/markdown"markdown-150725"
           (type/xml->Markdown (sexp [nil {:value "markdown-150725"}])))))
  (testing "type"
    (is (= :fhir/markdown (type/type #fhir/markdown""))))
  (testing "value is a System.String which is a String"
    (is (= "markdown-123745" (type/value #fhir/markdown"markdown-123745"))))
  (testing "to-json"
    (is (= "markdown-123745" (type/to-json #fhir/markdown"markdown-123745"))))
  (testing "to-xml"
    (is (= (sexp [nil {:value "markdown-123745"}])
           (type/to-xml #fhir/markdown"markdown-123745"))))
  (testing "equals"
    (is (= #fhir/markdown"175726" #fhir/markdown"175726")))
  (testing "print"
    (is (= "#fhir/markdown\"175718\"" (pr-str #fhir/markdown"175718"))))
  (testing "instance size"
    (is (= 56 (total-size #fhir/markdown"")))
    (is (= 64 (total-size #fhir/markdown"175718")))))


(deftest unsignedInt-test
  (testing "from XML"
    (is (= #fhir/unsignedInt 150725
           (type/xml->UnsignedInt (sexp [nil {:value "150725"}])))))
  (testing "type"
    (is (= :fhir/unsignedInt (type/type #fhir/unsignedInt 0))))
  (testing "value is a System.Integer which is a Integer"
    (is (= 160845 (type/value #fhir/unsignedInt 160845)))
    (is (instance? Integer (type/value #fhir/unsignedInt 160845))))
  (testing "to-json"
    (is (= 160845 (type/to-json #fhir/unsignedInt 160845))))
  (testing "to-xml"
    (is (= (sexp [nil {:value "160845"}])
           (type/to-xml #fhir/unsignedInt 160845))))
  (testing "equals"
    (is (= #fhir/unsignedInt 160845 #fhir/unsignedInt 160845)))
  (testing "print"
    (is (= "#fhir/unsignedInt 160845" (pr-str #fhir/unsignedInt 160845))))
  (testing "instance size"
    (is (= 16 (total-size #fhir/unsignedInt 0)))
    (is (= 16 (total-size #fhir/unsignedInt 175718)))))


(deftest positiveInt-test
  (testing "from XML"
    (is (= #fhir/positiveInt 150725
           (type/xml->PositiveInt (sexp [nil {:value "150725"}])))))
  (testing "type"
    (is (= :fhir/positiveInt (type/type #fhir/positiveInt 0))))
  (testing "value is a System.Integer which is a Integer"
    (is (= 160845 (type/value #fhir/positiveInt 160845)))
    (is (instance? Integer (type/value #fhir/positiveInt 160845))))
  (testing "to-json"
    (is (= 160845 (type/to-json #fhir/positiveInt 160845))))
  (testing "to-xml"
    (is (= (sexp [nil {:value "160845"}])
           (type/to-xml #fhir/positiveInt 160845))))
  (testing "equals"
    (is (= #fhir/positiveInt 160845 #fhir/positiveInt 160845)))
  (testing "print"
    (is (= "#fhir/positiveInt 160845" (pr-str #fhir/positiveInt 160845))))
  (testing "instance size"
    (is (= 16 (total-size #fhir/positiveInt 0)))
    (is (= 16 (total-size #fhir/positiveInt 175718)))))


(deftest uuid-test
  (testing "from XML"
    (is (= #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           (type/xml->Uuid (sexp [nil {:value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"}])))))
  (testing "type"
    (is (= :fhir/uuid (type/type #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))
  (testing "value is a System.String which is a String"
    (is (= "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           (type/value #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))
  (testing "to-json"
    (is (= "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           (type/to-json #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))
  (testing "to-xml"
    (is (= (sexp [nil {:value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"}])
           (type/to-xml #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))
  (testing "equals"
    (is (= #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")))
  (testing "instance size"
    (is (= 32 (total-size #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")))))


(def xhtml-element
  (sexp
    [::xhtml/div {:xmlns "http://www.w3.org/1999/xhtml"}
     [::xhtml/p "FHIR is cool."]]))


(deftest xhtml-test
  (testing "from XML"
    (is (= #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"
           (type/xml->Xhtml xhtml-element))))
  (testing "type"
    (is (= :fhir/xhtml (type/type #fhir/xhtml""))))
  (testing "value is a System.String which is a String"
    (is (= "xhtml-123745" (type/value #fhir/xhtml"xhtml-123745"))))
  (testing "to-json"
    (is (= "xhtml-123745" (type/to-json #fhir/xhtml"xhtml-123745"))))
  (testing "to-xml"
    (is (= xhtml-element (type/to-xml #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"))))
  (testing "equals"
    (is (= #fhir/xhtml"175726" #fhir/xhtml"175726")))
  (testing "print"
    (is (= "#fhir/xhtml\"175718\"" (pr-str #fhir/xhtml"175718"))))
  (testing "instance size"
    (is (= 56 (total-size #fhir/xhtml"")))
    (is (= 64 (total-size #fhir/xhtml"175718")))))
