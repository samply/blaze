(ns blaze.fhir.spec.type-test
  (:require
    [blaze.fhir.spec.type :as type]
    [blaze.fhir.spec.type.system :as system]
    [clojure.data.xml.name :as xml-name]
    [clojure.data.xml.node :as xml-node]
    [clojure.data.xml.prxml :as prxml]
    [clojure.spec.test.alpha :as st]
    [clojure.test :as test :refer [are deftest is testing]]
    [cuerdas.core :as str]
    [jsonista.core :as j])
  (:import
    [java.time Instant LocalDate LocalTime OffsetDateTime Year YearMonth
               ZoneOffset]
    [org.openjdk.jol.info ClassLayout GraphLayout]
    [com.google.common.hash Hashing]))


(xml-name/alias-uri 'xhtml "http://www.w3.org/1999/xhtml")


(st/instrument)


(defn- fixture [f]
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


(defn murmur3 [x]
  (let [hasher (.newHasher (Hashing/murmur3_32_fixed))]
    (type/hash-into x hasher)
    (Integer/toHexString (.asInt (.hash hasher)))))


(def ^:private object-mapper
  (j/object-mapper
    {:modules [type/fhir-module]}))


(defn- gen-json-string [x]
  (j/write-value-as-string x object-mapper))


(def ^:private sexp prxml/sexp-as-element)


(def ^:private string-extension
  #fhir/Extension{:url #fhir/uri"foo" :valueString "bar"})


(deftest nil-test
  (testing "all FhirType methods can be called on nil"
    (testing "type"
      (is (nil? (type/type nil))))
    (testing "value"
      (is (nil? (type/value nil))))
    (testing "to-json"
      (is (= "null" (gen-json-string nil))))
    (testing "to-xml"
      (is (nil? (type/to-xml nil))))
    (testing "hash-into"
      (is (= "0" (murmur3 nil))))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        nil
        nil))))


(deftest Object-test
  (testing "arbitrary instances have no fhir type"
    (is (nil? (type/type (Object.))))))


(deftest boolean-test
  (testing "type"
    (is (= :fhir/boolean (type/type true))))

  (testing "value"
    (is (= true (type/value true))))

  (testing "to-json"
    (is (= "true" (gen-json-string true))))

  (testing "to-xml"
    (are [b s] (= (sexp [nil {:value s}]) (type/to-xml b))
      true "true"
      false "false"))

  (testing "hash-into"
    (are [b hex] (= hex (murmur3 b))
      true "90690515"
      false "70fda443"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      true
      nil)))


(deftest integer-test
  (testing "from XML"
    (is (= #fhir/integer 1 (type/xml->Integer (sexp [nil {:value "1"}])))))

  (testing "type"
    (is (= :fhir/integer (type/type #fhir/integer 1))))

  (testing "value"
    (is (= 1 (type/value #fhir/integer 1))))

  (testing "to-json"
    (is (= "1" (gen-json-string #fhir/integer 1))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "1"}]) (type/to-xml #fhir/integer 1))))

  (testing "hash-into"
    (are [i hex] (= hex (murmur3 i))
      (int 0) "ab61a435"
      (int 1) "f9ff6b7c"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      (int 0)
      nil))

  (testing "instance size"
    (is (= 16 (total-size #fhir/integer 1)))))


(deftest long-test
  (testing "from XML"
    (is (= #fhir/integer 1 (type/xml->Long (sexp [nil {:value "1"}])))))

  (testing "type"
    (is (= :fhir/long (type/type #fhir/long 1))))

  (testing "value"
    (is (= 1 (type/value #fhir/long 1))))

  (testing "to-json"
    (is (= "1" (gen-json-string #fhir/long 1))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "1"}]) (type/to-xml #fhir/long 1))))

  (testing "hash-into"
    (are [i hex] (= hex (murmur3 i))
      0 "9bc977cc"
      1 "fac0175c"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      0
      nil))

  (testing "instance size"
    (is (= 24 (total-size #fhir/long 1)))))


(deftest string-test
  (testing "string?"
    (is (type/string? "")))

  (testing "from XML"
    (is (= "142214" (type/xml->String (sexp [nil {:value "142214"}])))))

  (testing "type"
    (is (= :fhir/string (type/type ""))))

  (testing "value"
    (is (= "175227" (type/value "175227"))))

  (testing "to-json"
    (is (= "\"105406\"" (gen-json-string "105406"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "121344"}]) (type/to-xml "121344"))))

  (testing "hash-into"
    (are [s hex] (= hex (murmur3 s))
      "" "126916b"
      "foo" "ba7851a6"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      "151736"
      nil))

  (testing "instance size"
    (are [s size] (= size (total-size s))
      "" 40
      "a" 48
      (str/repeat "a" 8) 48
      (str/repeat "a" 9) 56)))


(deftest decimal-test
  (testing "decimal?"
    (is (type/decimal? 1M)))

  (testing "from XML"
    (is (= 1M (type/xml->Decimal (sexp [nil {:value "1"}])))))

  (testing "type"
    (is (= :fhir/decimal (type/type 1M))))

  (testing "value"
    (is (= 1M (type/value 1M))))

  (testing "to-json"
    (are [decimal json] (= json (gen-json-string decimal))
      1M "1"
      1.1M "1.1"))

  (testing "to-xml"
    (is (= (sexp [nil {:value "1.1"}]) (type/to-xml 1.1M))))

  (testing "hash-into"
    (are [d hex] (= hex (murmur3 d))
      0M "7e564b82"
      1M "f2f4ddc7"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      0M
      nil))

  (testing "instance size"
    (is (= 40 (total-size 1.1M)))))


(deftest uri-test
  (testing "uri?"
    (is (type/uri? #fhir/uri"")))

  (testing "from XML"
    (is (= #fhir/uri"142307" (type/xml->Uri (sexp [nil {:value "142307"}])))))

  (testing "type"
    (is (= :fhir/uri (type/type #fhir/uri""))))

  (testing "value"
    (is (= "105614" (type/value #fhir/uri"105614"))))

  (testing "to-json"
    (is (= "\"105846\"" (gen-json-string #fhir/uri"105846"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "105846"}]) (type/to-xml #fhir/uri"105846"))))

  (testing "equals"
    (is (= #fhir/uri"142334" #fhir/uri"142334"))
    (is (not= #fhir/uri"142334" #fhir/uri"215930"))
    (is (not= #fhir/uri"142334" "142334")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/uri"" "51a99a01"
      #fhir/uri"foo" "dc60f982"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/uri"151758"
      nil))

  (testing "print"
    (is (= "#fhir/uri\"142600\"" (pr-str #fhir/uri"142600"))))

  (testing "instance size"
    (is (= 56 (total-size #fhir/uri"")))
    (is (= 64 (total-size #fhir/uri"a")))))


(deftest url-test
  (testing "url?"
    (is (type/url? #fhir/url"")))

  (testing "from XML"
    (is (= #fhir/url"142307" (type/xml->Url (sexp [nil {:value "142307"}])))))

  (testing "type"
    (is (= :fhir/url (type/type #fhir/url""))))

  (testing "value"
    (is (= "105614" (type/value #fhir/url"105614"))))

  (testing "to-json"
    (is (= "\"105846\"" (gen-json-string #fhir/url"105846"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "105846"}]) (type/to-xml #fhir/url"105846"))))

  (testing "equals"
    (is (= #fhir/url"142334" #fhir/url"142334"))
    (is (not= #fhir/url"142334" #fhir/url"220025"))
    (is (not= #fhir/url"142334" "142334")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/url"" "39b10d82"
      #fhir/url"foo" "7acc4e54"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/url"151809"
      nil))

  (testing "print"
    (is (= "#fhir/url\"142600\"" (pr-str #fhir/url"142600"))))

  (testing "instance size"
    (is (= 56 (total-size #fhir/url"")))
    (is (= 64 (total-size #fhir/url"a")))))


(deftest canonical-test
  (testing "canonical?"
    (is (type/canonical? #fhir/canonical"")))

  (testing "from XML"
    (is (= #fhir/canonical"142307" (type/xml->Canonical (sexp [nil {:value "142307"}])))))

  (testing "type"
    (is (= :fhir/canonical (type/type #fhir/canonical""))))

  (testing "value"
    (is (= "105614" (type/value #fhir/canonical"105614"))))

  (testing "to-json"
    (is (= "\"105846\"" (gen-json-string #fhir/canonical"105846"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "105846"}]) (type/to-xml #fhir/canonical"105846"))))

  (testing "equals"
    (is (= #fhir/canonical"142334" #fhir/canonical"142334"))
    (is (not= #fhir/canonical"142334" #fhir/canonical"220056"))
    (is (not= #fhir/canonical"142334" "142334")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/canonical"" "53c915fd"
      #fhir/canonical"foo" "e42e9c7e"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/canonical"151819"
      nil))

  (testing "print"
    (is (= "#fhir/canonical\"142600\"" (pr-str #fhir/canonical"142600"))))

  (testing "instance size"
    (is (= 56 (total-size #fhir/canonical"")))
    (is (= 64 (total-size #fhir/canonical"a")))))


(deftest base64Binary-test
  (testing "base64Binary?"
    (is (type/base64Binary? #fhir/base64Binary"MTA1NjE0Cg==")))

  (testing "from XML"
    (is (= #fhir/base64Binary"MTA1NjE0Cg=="
           (type/xml->Base64Binary (sexp [nil {:value "MTA1NjE0Cg=="}])))))

  (testing "type"
    (is (= :fhir/base64Binary (type/type #fhir/base64Binary""))))

  (testing "value"
    (is (= "MTA1NjE0Cg==" (type/value #fhir/base64Binary"MTA1NjE0Cg=="))))

  (testing "to-json"
    (is (= "\"MTA1NjE0Cg==\"" (gen-json-string #fhir/base64Binary"MTA1NjE0Cg=="))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "MTA1NjE0Cg=="}]) (type/to-xml #fhir/base64Binary"MTA1NjE0Cg=="))))

  (testing "equals"
    (is (= #fhir/base64Binary"MTA1NjE0Cg==" #fhir/base64Binary"MTA1NjE0Cg=="))
    (is (not= #fhir/base64Binary"MTA1NjE0Cg==" #fhir/base64Binary"YQo="))
    (is (not= #fhir/base64Binary"MTA1NjE0Cg==" "MTA1NjE0Cg==")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/base64Binary"" "339ff20a"
      #fhir/base64Binary"YQo=" "ed565602"
      #fhir/base64Binary"MTA1NjE0Cg===" "24568b10"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/base64Binary"YQo="
      nil))

  (testing "print"
    (is (= "#fhir/base64Binary\"YQo=\"" (pr-str #fhir/base64Binary"YQo="))))

  (testing "instance size"
    (is (= 56 (total-size #fhir/base64Binary"")))
    (is (= 64 (total-size #fhir/base64Binary"YQo=")))
    (is (= 72 (total-size #fhir/base64Binary"MTA1NjE0Cg==")))))


(deftest instant-test
  (testing "instant?"
    (is (type/instant? Instant/EPOCH)))
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
    (are [instant json] (= json (gen-json-string instant))
      (type/->Instant "2020-01-01T00:00:00+02:00") "\"2020-01-01T00:00:00+02:00\""
      Instant/EPOCH "\"1970-01-01T00:00:00Z\""))
  (testing "to-xml"
    (is (= (sexp [nil {:value "2020-01-01T00:00:00+02:00"}])
           (type/to-xml (type/->Instant "2020-01-01T00:00:00+02:00"))))
    (is (= (sexp [nil {:value "1970-01-01T00:00:00Z"}])
           (type/to-xml Instant/EPOCH))))
  (testing "equals"
    (is (= (type/->Instant "2020-01-01T00:00:00+02:00")
           (type/->Instant "2020-01-01T00:00:00+02:00")))
    (is (not= (type/->Instant "2020-01-01T00:00:00+01:00")
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
    (are [x hex] (= hex (murmur3 x))
      (type/->Instant "2020-01-01T00:00:00+00:00") "d81f6bc2"
      (type/->Instant "2020-01-01T00:00:00+01:00") "4225df0d"
      (type/->Instant "2020-01-01T00:00:00Z") "d81f6bc2"
      (type/->Instant "1970-01-01T00:00:00Z") "93344244"
      Instant/EPOCH "93344244"))
  (testing "references"
    (are [x refs] (= refs (type/references x))
      Instant/EPOCH
      nil)))


(deftest date-test
  (testing "with year precision"
    (testing "date?"
      (is (type/date? #fhir/date"2010")))
    (testing "from XML"
      (is (= #fhir/date"2010" (type/xml->Date (sexp [nil {:value "2010"}])))))
    (testing "type"
      (is (= :fhir/date (type/type #fhir/date"2020"))))
    (testing "value"
      (is (= (Year/of 2020) (type/value #fhir/date"2020"))))
    (testing "to-json"
      (is (= "\"2020\"" (gen-json-string #fhir/date"2020"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020"}]) (type/to-xml #fhir/date"2020"))))
    (testing "equals"
      (is (= #fhir/date"2020" #fhir/date"2020"))
      (is (not= #fhir/date"2020" #fhir/date"2021"))
      (is (not= #fhir/date"2020" "2020")))
    (testing "instance size"
      (is (= 16 (total-size #fhir/date"2020"))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date"2020" "c92be432"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/date"2020"
        nil)))

  (testing "with year-month precision"
    (testing "date?"
      (is (type/date? #fhir/date"2010-04")))
    (testing "from XML"
      (is (= #fhir/date"2010-04"
             (type/xml->Date (sexp [nil {:value "2010-04"}])))))
    (testing "type"
      (is (= :fhir/date (type/type #fhir/date"2020-01"))))
    (testing "value"
      (is (= (YearMonth/of 2020 1) (type/value #fhir/date"2020-01"))))
    (testing "to-json"
      (is (= "\"2020-01\"" (gen-json-string #fhir/date"2020-01"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01"}]) (type/to-xml #fhir/date"2020-01"))))
    (testing "equals"
      (is (= #fhir/date"2020-01" #fhir/date"2020-01"))
      (is (not= #fhir/date"2020-01" #fhir/date"2020-02"))
      (is (not= #fhir/date"2020-01" "2020-01")))
    (testing "instance size"
      (is (= 24 (total-size #fhir/date"2020-01"))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date"2020-01" "fbcdf97f"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/date"2020-01"
        nil)))

  (testing "with date precision"
    (testing "date?"
      (is (type/date? #fhir/date"2010-05-15")))
    (testing "from XML"
      (is (= #fhir/date"2010-05-15"
             (type/xml->Date (sexp [nil {:value "2010-05-15"}])))))
    (testing "type"
      (is (= :fhir/date (type/type #fhir/date"2020-01-01"))))
    (testing "value"
      (is (= (LocalDate/of 2020 1 1) (type/value #fhir/date"2020-01-01"))))
    (testing "to-json"
      (is (= "\"2020-01-01\"" (gen-json-string #fhir/date"2020-01-01"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01-01"}])
             (type/to-xml #fhir/date"2020-01-01"))))
    (testing "equals"
      (is (= #fhir/date"2020-01-01" #fhir/date"2020-01-01"))
      (is (not= #fhir/date"2020-01-01" #fhir/date"2020-01-02"))
      (is (not= #fhir/date"2020-01-01" "2020-01-01")))
    (testing "instance size"
      (is (= 24 (total-size #fhir/date"2020-01-01"))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/date"2020-01-01" "cd20e081"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/date"2020-01-01"
        nil))))


(deftest dateTime-test
  (testing "with year precision"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2010")))
    (testing "from XML"
      (is (= #fhir/dateTime"2010"
             (type/xml->DateTime (sexp [nil {:value "2010"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020"))))
    (testing "value"
      (is (= (system/date-time 2020) (type/value #fhir/dateTime"2020"))))
    (testing "to-json"
      (is (= "\"2020\"" (gen-json-string #fhir/dateTime"2020"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020"}]) (type/to-xml #fhir/dateTime"2020"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020" #fhir/dateTime"2020"))
      (is (not= #fhir/dateTime"2020" #fhir/dateTime"2021"))
      (is (not= #fhir/dateTime"2020" "2020")))
    (testing "instance size"
      (is (= 32 (total-size #fhir/dateTime"2020"))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020" "41e906ff"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020"
        nil))
    (comment
      (quick-bench (type/->DateTime "2020"))))

  (testing "with year-month precision"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2010-04")))
    (testing "from XML"
      (is (= #fhir/dateTime"2010-04"
             (type/xml->DateTime (sexp [nil {:value "2010-04"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01"))))
    (testing "value"
      (is (= (system/date-time 2020 1)
             (type/value #fhir/dateTime"2020-01"))))
    (testing "to-json"
      (is (= "\"2020-01\"" (gen-json-string #fhir/dateTime"2020-01"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01"}])
             (type/to-xml #fhir/dateTime"2020-01"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01" #fhir/dateTime"2020-01"))
      (is (not= #fhir/dateTime"2020-01" #fhir/dateTime"2020-02"))
      (is (not= #fhir/dateTime"2020-01" "2020-01")))
    (testing "instance size"
      (is (= 40 (total-size #fhir/dateTime"2020-01"))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01" "9d6c5bd3"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01"
        nil)))

  (testing "with date precision"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2010-05-15")))
    (testing "from XML"
      (is (= #fhir/dateTime"2010-05-15"
             (type/xml->DateTime (sexp [nil {:value "2010-05-15"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01-01"))))
    (testing "value"
      (is (= (system/date-time 2020 1 1)
             (type/value #fhir/dateTime"2020-01-01"))))
    (testing "to-json"
      (is (= "\"2020-01-01\"" (gen-json-string #fhir/dateTime"2020-01-01"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01-01"}])
             (type/to-xml #fhir/dateTime"2020-01-01"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01" #fhir/dateTime"2020-01-01")))
    (testing "instance size"
      (is (= 40 (total-size #fhir/dateTime"2020-01-01"))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01" "39fe9bdb"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01"
        nil))
    (comment
      (quick-bench (type/->DateTime "2020-01-01"))))

  (testing "without timezone"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2020-01-01T00:00:00")))
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01-01T00:00:00"))))
    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01-01T00:00:00"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00"
             #fhir/dateTime"2020-01-01T00:00:00")))
    (testing "instance size"
      (is (= 72 (total-size #fhir/dateTime"2020-01-01T00:00:00"))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00" "da537591"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00"
        nil)))

  (testing "without timezone but millis"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2020-01-01T00:00:00.001")))
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00.001"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00.001"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01-01T00:00:00.000"))))
    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00.001\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00.001"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01-01T00:00:00.001"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00.001"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00.000"
             #fhir/dateTime"2020-01-01T00:00:00.000")))
    (testing "instance size"
      (is (= 72 (total-size #fhir/dateTime"2020-01-01T00:00:00.000"))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00.000" "da537591"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00.000"
        nil)))

  (testing "with zulu timezone"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2020-01-01T00:00:00Z")))
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00Z"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00Z"}])))))
    (testing "type"
      (is (= :fhir/dateTime (type/type #fhir/dateTime"2020-01-01T00:00:00Z"))))
    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00Z\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00Z"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01-01T00:00:00Z"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00Z"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00Z"
             #fhir/dateTime"2020-01-01T00:00:00Z")))
    (testing "instance size taking into account shared offsets"
      (is (= 96 (- (total-size #fhir/dateTime"2020-01-01T00:00:00Z")
                   (total-size ZoneOffset/UTC)))))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00Z" "d541a45"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00Z"
        nil)))

  (testing "with positive timezone offset"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2020-01-01T00:00:00+01:00")))
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00+01:00"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00+01:00"}])))))
    (testing "type"
      (is (= :fhir/dateTime
             (type/type #fhir/dateTime"2020-01-01T00:00:00+01:00"))))
    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00+01:00\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00+01:00"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01-01T00:00:00+01:00"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00+01:00"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00+01:00"
             #fhir/dateTime"2020-01-01T00:00:00+01:00")))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00+01:00" "9c535d0d"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00+01:00"
        nil)))

  (testing "with negative timezone offset"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2020-01-01T00:00:00-01:00")))
    (testing "from XML"
      (is (= #fhir/dateTime"2020-01-01T00:00:00-01:00"
             (type/xml->DateTime (sexp [nil {:value "2020-01-01T00:00:00-01:00"}])))))
    (testing "type"
      (is (= :fhir/dateTime
             (type/type #fhir/dateTime"2020-01-01T00:00:00-01:00"))))
    (testing "to-json"
      (is (= "\"2020-01-01T00:00:00-01:00\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00-01:00"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01-01T00:00:00-01:00"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00-01:00"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00-01:00"
             #fhir/dateTime"2020-01-01T00:00:00-01:00")))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00-01:00" "839fd8a6"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00-01:00"
        nil)))

  (testing "with zulu timezone and millis"
    (testing "date-time?"
      (is (type/date-time? #fhir/dateTime"2020-01-01T00:00:00.001Z")))
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
      (is (= "\"2020-01-01T00:00:00.001Z\""
             (gen-json-string #fhir/dateTime"2020-01-01T00:00:00.001Z"))))
    (testing "to-xml"
      (is (= (sexp [nil {:value "2020-01-01T00:00:00.001Z"}])
             (type/to-xml #fhir/dateTime"2020-01-01T00:00:00.001Z"))))
    (testing "equals"
      (is (= #fhir/dateTime"2020-01-01T00:00:00.001Z"
             #fhir/dateTime"2020-01-01T00:00:00.001Z")))
    (testing "hash-into"
      (are [x hex] (= hex (murmur3 x))
        #fhir/dateTime"2020-01-01T00:00:00.001Z" "f46a0b1b"))
    (testing "references"
      (are [x refs] (= refs (type/references x))
        #fhir/dateTime"2020-01-01T00:00:00.001Z"
        nil)))

  (testing "with extensions"
    (let [extended-date-time (type/->DateTime nil [string-extension] "2020")
          extended-date-time-element (xml-node/element nil {:value "2020"} string-extension)]
      (testing "date-time?"
        (is (type/date-time? extended-date-time)))
      (testing "type"
        (is (= :fhir/dateTime (type/type extended-date-time))))
      (testing "value"
        (is (= (system/date-time 2020) (type/value extended-date-time))))
      (testing "to-json"
        (is (= "\"2020\"" (gen-json-string extended-date-time))))
      (testing "to-xml"
        (is (= extended-date-time-element (type/to-xml extended-date-time))))
      (testing "equals"
        (is (= (type/->DateTime nil [string-extension] "2020") extended-date-time)))
      (testing "hash-into"
        (are [x hex] (= hex (murmur3 x))
          extended-date-time "c10cb51"))
      (testing "references"
        (are [x refs] (= refs (type/references x))
          extended-date-time
          []))
      (comment
        (quick-bench extended-date-time)))))


(deftest time-test
  (testing "time?"
    (is (type/time? #fhir/time"13:53:21")))

  (testing "from XML"
    (is (= #fhir/time"13:53:21" (type/xml->Time (sexp [nil {:value "13:53:21"}])))))

  (testing "type"
    (is (= :fhir/time (type/type #fhir/time"13:53:21"))))

  (testing "value is a System.Time which is a LocalTime"
    (is (= (LocalTime/of 13 53 21) (type/value #fhir/time"13:53:21"))))

  (testing "to-json"
    (is (= "\"13:53:21\"" (gen-json-string #fhir/time"13:53:21"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "13:53:21"}])
           (type/to-xml #fhir/time"13:53:21"))))

  (testing "equals"
    (is (= #fhir/time"13:53:21" #fhir/time"13:53:21"))
    (is (not= #fhir/time"13:53:21" #fhir/time"13:53:22"))
    (is (not= #fhir/time"13:53:21" "13:53:21")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/time"13:53:21" "faa37be9"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/time"13:53:21"
      nil))

  (testing "instance size"
    (is (= 24 (total-size #fhir/time"13:53:21")))))


(def gender-extension
  #fhir/Extension
      {:url #fhir/uri"http://fhir.de/StructureDefinition/gender-amtlich-de"
       :value
       #fhir/Coding
           {:system #fhir/uri"http://fhir.de/CodeSystem/gender-amtlich-de"
            :code #fhir/code"D"
            :display "divers"}})


(def extended-gender-code
  (type/map->ExtendedCode {:extension [gender-extension] :value "other"}))


(def extended-gender-code-element
  (xml-node/element nil {:value "other"} gender-extension))


(deftest code-test
  (testing "code?"
    (are [x] (type/code? x)
      #fhir/code""
      #fhir/code{}))

  (testing "from XML"
    (is (= #fhir/code"code-150725"
           (type/xml->Code (sexp [nil {:value "code-150725"}])))))

  (testing "type"
    (is (= :fhir/code (type/type #fhir/code""))))

  (testing "value is a System.String which is a String"
    (is (= "code-123745" (type/value #fhir/code"code-123745")))
    (is (= "code-170217" (type/value #fhir/code{:value "code-170217"}))))

  (testing "to-json"
    (are [code json] (= json (gen-json-string code))
      #fhir/code"code-123745" "\"code-123745\""
      extended-gender-code "\"other\""))

  (testing "to-xml"
    (is (= (sexp [nil {:value "code-123745"}])
           (type/to-xml #fhir/code"code-123745")))
    (is (= extended-gender-code-element (type/to-xml extended-gender-code))))

  (testing "equals"
    (is (= #fhir/code"175726" #fhir/code"175726"))
    (is (not= #fhir/code"175726" #fhir/code"165817"))
    (is (not= #fhir/code"175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/code"175726" "9c96c20f"
      #fhir/code{} "70a5e0a7"
      #fhir/code{:id "170837"} "3f64cec2"
      #fhir/code{:extension [#fhir/Extension{}]} "a752bfb7"
      #fhir/code{:value "170935"} "767a99da"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/code"code-150839"
      nil

      #fhir/code
          {:extension
           [#fhir/Extension
               {:value #fhir/Reference{:reference "Patient/1"}}]}
      [["Patient" "1"]]))

  (testing "print"
    (is (= "#fhir/code\"175718\"" (pr-str #fhir/code"175718"))))

  (testing "instance size"
    (is (= 56 (total-size #fhir/code"")))
    (is (= 64 (total-size #fhir/code"175718")))))


(deftest oid-test
  (testing "oid?"
    (is (type/oid? #fhir/oid"")))

  (testing "from XML"
    (is (= #fhir/oid"oid-150725"
           (type/xml->Oid (sexp [nil {:value "oid-150725"}])))))

  (testing "type"
    (is (= :fhir/oid (type/type #fhir/oid""))))

  (testing "value is a System.String which is a String"
    (is (= "oid-123745" (type/value #fhir/oid"oid-123745"))))

  (testing "to-json"
    (is (= "\"oid-123745\"" (gen-json-string #fhir/oid"oid-123745"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "oid-123745"}])
           (type/to-xml #fhir/oid"oid-123745"))))

  (testing "equals"
    (is (= #fhir/oid"175726" #fhir/oid"175726"))
    (is (not= #fhir/oid"175726" #fhir/oid"171055"))
    (is (not= #fhir/oid"175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/oid"175726" "a73ea817"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/oid"151329"
      nil))

  (testing "print"
    (is (= "#fhir/oid\"175718\"" (pr-str #fhir/oid"175718"))))

  (testing "instance size"
    (testing "instance size"
      (is (= 56 (total-size #fhir/oid"")))
      (is (= 64 (total-size #fhir/oid"175718"))))))


(deftest id-test
  (testing "id?"
    (is (type/id? #fhir/id"")))

  (testing "from XML"
    (is (= #fhir/id"id-150725"
           (type/xml->Id (sexp [nil {:value "id-150725"}])))))

  (testing "type"
    (is (= :fhir/id (type/type #fhir/id""))))

  (testing "value is a System.String which is a String"
    (is (= "id-123745" (type/value #fhir/id"id-123745"))))

  (testing "to-json"
    (is (= "\"id-123745\"" (gen-json-string #fhir/id"id-123745"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "id-123745"}])
           (type/to-xml #fhir/id"id-123745"))))

  (testing "equals"
    (is (= #fhir/id"175726" #fhir/id"175726"))
    (is (not= #fhir/id"175726" #fhir/id"171108"))
    (is (not= #fhir/id"175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/id"175726" "e56cbac6"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/id"151408"
      nil))

  (testing "print"
    (is (= "#fhir/id\"175718\"" (pr-str #fhir/id"175718"))))

  (testing "instance size"
    (is (= 56 (total-size #fhir/id"")))
    (is (= 64 (total-size #fhir/id"175718")))))


(deftest markdown-test
  (testing "markdown?"
    (is (type/markdown? #fhir/markdown"")))

  (testing "from XML"
    (is (= #fhir/markdown"markdown-150725"
           (type/xml->Markdown (sexp [nil {:value "markdown-150725"}])))))

  (testing "type"
    (is (= :fhir/markdown (type/type #fhir/markdown""))))

  (testing "value is a System.String which is a String"
    (is (= "markdown-123745" (type/value #fhir/markdown"markdown-123745"))))

  (testing "to-json"
    (is (= "\"markdown-123745\""
           (gen-json-string #fhir/markdown"markdown-123745"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "markdown-123745"}])
           (type/to-xml #fhir/markdown"markdown-123745"))))

  (testing "equals"
    (is (= #fhir/markdown"175726" #fhir/markdown"175726"))
    (is (not= #fhir/markdown"175726" #fhir/markdown"171153"))
    (is (not= #fhir/markdown"175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/markdown"175726" "444928f7"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/markdown"151424"
      nil))

  (testing "print"
    (is (= "#fhir/markdown\"175718\"" (pr-str #fhir/markdown"175718"))))

  (testing "instance size"
    (is (= 56 (total-size #fhir/markdown"")))
    (is (= 64 (total-size #fhir/markdown"175718")))))


(deftest unsignedInt-test
  (testing "unsignedInt?"
    (is (type/unsignedInt? #fhir/unsignedInt 0)))

  (testing "from XML"
    (is (= #fhir/unsignedInt 150725
           (type/xml->UnsignedInt (sexp [nil {:value "150725"}])))))

  (testing "type"
    (is (= :fhir/unsignedInt (type/type #fhir/unsignedInt 0))))

  (testing "value is a System.Integer which is a Integer"
    (is (= 160845 (type/value #fhir/unsignedInt 160845)))
    (is (instance? Integer (type/value #fhir/unsignedInt 160845))))

  (testing "to-json"
    (is (= "160845" (gen-json-string #fhir/unsignedInt 160845))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "160845"}])
           (type/to-xml #fhir/unsignedInt 160845))))

  (testing "equals"
    (is (= #fhir/unsignedInt 160845 #fhir/unsignedInt 160845))
    (is (not= #fhir/unsignedInt 160845 #fhir/unsignedInt 171218))
    (is (not= #fhir/unsignedInt 160845 160845)))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/unsignedInt 160845 "10a52aa2"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/unsignedInt 151440
      nil))

  (testing "print"
    (is (= "#fhir/unsignedInt 160845" (pr-str #fhir/unsignedInt 160845))))

  (testing "instance size"
    (is (= 16 (total-size #fhir/unsignedInt 0)))
    (is (= 16 (total-size #fhir/unsignedInt 175718)))))


(deftest positiveInt-test
  (testing "positiveInt?"
    (is (type/positiveInt? #fhir/positiveInt 0)))

  (testing "from XML"
    (is (= #fhir/positiveInt 150725
           (type/xml->PositiveInt (sexp [nil {:value "150725"}])))))

  (testing "type"
    (is (= :fhir/positiveInt (type/type #fhir/positiveInt 0))))

  (testing "value is a System.Integer which is a Integer"
    (is (= 160845 (type/value #fhir/positiveInt 160845)))
    (is (instance? Integer (type/value #fhir/positiveInt 160845))))

  (testing "to-json"
    (is (= "160845" (gen-json-string #fhir/positiveInt 160845))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "160845"}])
           (type/to-xml #fhir/positiveInt 160845))))

  (testing "equals"
    (is (= #fhir/positiveInt 160845 #fhir/positiveInt 160845))
    (is (not= #fhir/positiveInt 160845 #fhir/positiveInt 171237))
    (is (not= #fhir/positiveInt 160845 160845)))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/positiveInt 160845 "8c218d7d"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/positiveInt 151500
      nil))

  (testing "print"
    (is (= "#fhir/positiveInt 160845" (pr-str #fhir/positiveInt 160845))))

  (testing "instance size"
    (is (= 16 (total-size #fhir/positiveInt 0)))
    (is (= 16 (total-size #fhir/positiveInt 175718)))))


(deftest uuid-test
  (testing "uuid?"
    (is (type/uuid? #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")))

  (testing "from XML"
    (is (= #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           (type/xml->Uuid (sexp [nil {:value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"}])))))

  (testing "type"
    (is (= :fhir/uuid (type/type #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "value is a System.String which is a String"
    (is (= "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           (type/value #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "to-json"
    (is (= "\"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3\""
           (gen-json-string #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "to-xml"
    (is (= (sexp [nil {:value "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"}])
           (type/to-xml #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))))

  (testing "equals"
    (is (= #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
           #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"))
    (is (not= #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
              #fhir/uuid"urn:uuid:ccd4a49d-a288-4387-b842-56dd0f896851"))
    (is (not= #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3"
              "urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3" "f894ff2b"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/uuid"urn:uuid:89ddf6ab-8813-4c75-9500-dd07560fe817"
      nil))

  (testing "instance size"
    (is (= 32 (total-size #fhir/uuid"urn:uuid:6d270b7d-bf7d-4c95-8e30-4d87360d47a3")))))


(def xhtml-element
  (sexp
    [::xhtml/div {:xmlns "http://www.w3.org/1999/xhtml"}
     [::xhtml/p "FHIR is cool."]]))


(deftest xhtml-test
  (testing "xhtml?"
    (is (type/xhtml? #fhir/xhtml"xhtml-123745")))

  (testing "from XML"
    (is (= #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"
           (type/xml->Xhtml xhtml-element))))

  (testing "type"
    (is (= :fhir/xhtml (type/type #fhir/xhtml""))))

  (testing "value is a System.String which is a String"
    (is (= "xhtml-123745" (type/value #fhir/xhtml"xhtml-123745"))))

  (testing "to-json"
    (is (= "\"xhtml-123745\"" (gen-json-string #fhir/xhtml"xhtml-123745"))))

  (testing "to-xml"
    (is (= xhtml-element (type/to-xml #fhir/xhtml"<div xmlns=\"http://www.w3.org/1999/xhtml\"><p>FHIR is cool.</p></div>"))))

  (testing "equals"
    (is (= #fhir/xhtml"175726" #fhir/xhtml"175726"))
    (is (not= #fhir/xhtml"175726" #fhir/xhtml"171511"))
    (is (not= #fhir/xhtml"175726" "175726")))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/xhtml"175726" "e90ddf05"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/xhtml"151551"
      nil))

  (testing "print"
    (is (= "#fhir/xhtml\"175718\"" (pr-str #fhir/xhtml"175718"))))

  (testing "instance size"
    (is (= 56 (total-size #fhir/xhtml"")))
    (is (= 64 (total-size #fhir/xhtml"175718")))))


(deftest attachment-test
  (testing "type"
    (is (= :fhir/Attachment (type/type #fhir/Attachment{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Attachment{}
      "af56fc23"

      #fhir/Attachment{:id "id-204201"}
      "fb9177d8"

      #fhir/Attachment{:extension [#fhir/Extension{}]}
      "4947bc16"

      #fhir/Attachment{:contentType #fhir/code"text/plain"}
      "21d5985e"

      #fhir/Attachment{:language #fhir/code"de"}
      "223e2e7f"

      #fhir/Attachment{:data #fhir/base64Binary"MTA1NjE0Cg=="}
      "d2a23543"

      #fhir/Attachment{:url #fhir/url"url-210424"}
      "67f9de2f"

      #fhir/Attachment{:size #fhir/unsignedInt 1}
      "180724c5"

      #fhir/Attachment{:hash #fhir/base64Binary"MTA1NjE0Cg=="}
      "26e1ef66"

      #fhir/Attachment{:title "title-210622"}
      "fce4d064"

      #fhir/Attachment{:creation #fhir/dateTime"2021"}
      "1f9bf068"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Attachment{}
      []))

  (testing "instance size"
    (is (= 72 (total-size #fhir/Attachment{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Attachment{} "#fhir/Attachment{}"
      #fhir/Attachment{:id "212329"} "#fhir/Attachment{:id \"212329\"}")))


(deftest extension-test
  (testing "type"
    (is (= :fhir/Extension (type/type #fhir/Extension{}))))

  (testing "to-json"
    (are [code json] (= json (gen-json-string code))
      #fhir/Extension{} "{}"
      #fhir/Extension{:id "id-162531"} "{\"id\":\"id-162531\"}"))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Extension{}
      "9b6ea882"

      #fhir/Extension{:id "id-204201"}
      "495cd278"

      #fhir/Extension{:extension [#fhir/Extension{}]}
      "af661e95"

      #fhir/Extension{:extension [#fhir/Extension{} #fhir/Extension{}]}
      "96fd01bd"

      #fhir/Extension{:url "url-130945"}
      "8204427a"

      #fhir/Extension{:value #fhir/code"value-130953"}
      "befce87a"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Extension{}
      []))

  (testing "instance size"
    (is (= 48 (total-size #fhir/Extension{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Extension{} "#fhir/Extension{}"
      #fhir/Extension{:id "212329"} "#fhir/Extension{:id \"212329\"}")))


(deftest coding-test
  (testing "type"
    (is (= :fhir/Coding (type/type #fhir/Coding{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Coding{}
      "24e7e891"

      #fhir/Coding{:id "id-204201"}
      "c1c82c65"

      #fhir/Coding{:extension [#fhir/Extension{}]}
      "e1d440bb"

      #fhir/Coding{:system #fhir/uri"system-202808"}
      "da808d2d"

      #fhir/Coding{:version #fhir/uri"version-154317"}
      "93fc58d9"

      #fhir/Coding{:code #fhir/code"code-202828"}
      "74e3328d"

      #fhir/Coding{:display "display-154256"}
      "baac923d"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Coding{}
      []))

  (testing "instance size"
    (is (= 56 (total-size #fhir/Coding{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Coding{} "#fhir/Coding{}"
      #fhir/Coding{:id "212329"} "#fhir/Coding{:id \"212329\"}")))


(deftest codeable-concept-test
  (testing "type"
    (is (= :fhir/CodeableConcept (type/type #fhir/CodeableConcept{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/CodeableConcept{}
      "9b6ea882"

      #fhir/CodeableConcept{:id "id-141755"}
      "d9ac742f"

      #fhir/CodeableConcept{:extension [#fhir/Extension{}]}
      "af661e95"

      #fhir/CodeableConcept{:coding [#fhir/Coding{}]}
      "9c4509ed"

      #fhir/CodeableConcept{:text "text-153829"}
      "fe2e61f1"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/CodeableConcept{}
      []))

  (testing "instance size"
    (is (= 48 (total-size #fhir/CodeableConcept{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/CodeableConcept{} "#fhir/CodeableConcept{}"
      #fhir/CodeableConcept{:id "212329"} "#fhir/CodeableConcept{:id \"212329\"}")))


(deftest quantity-test
  (testing "type"
    (is (= :fhir/Quantity (type/type #fhir/Quantity{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Quantity{}
      "1ddef3ed"

      #fhir/Quantity{:id "id-141848"}
      "abb59da1"

      #fhir/Quantity{:extension [#fhir/Extension{}]}
      "4f5028ac"

      #fhir/Quantity{:value 1M}
      "4adf97ab"

      #fhir/Quantity{:comparator #fhir/code"comparator-153342"}
      "6339e3e8"

      #fhir/Quantity{:unit "unit-153351"}
      "d8f92891"

      #fhir/Quantity{:system #fhir/uri"system-153337"}
      "98f918ba"

      #fhir/Quantity{:code #fhir/code"code-153427"}
      "7ff49528"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Quantity{}
      []))

  (testing "instance size"
    (is (= 56 (total-size #fhir/Quantity{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Quantity{} "#fhir/Quantity{}"
      #fhir/Quantity{:id "212329"} "#fhir/Quantity{:id \"212329\"}")))


(deftest period-test
  (testing "type"
    (is (= :fhir/Period (type/type #fhir/Period{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Period{}
      "e5f76205"

      #fhir/Period{:id "id-130710"}
      "29c53420"

      #fhir/Period{:extension [#fhir/Extension{}]}
      "92e4ba37"

      #fhir/Period{:start #fhir/dateTime"2020"}
      "f1b7c952"

      #fhir/Period{:end #fhir/dateTime"2020"}
      "434787dd"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Period{}
      []))

  (testing "instance size"
    (is (= 48 (total-size #fhir/Period{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Period{} "#fhir/Period{}"
      #fhir/Period{:id "212329"} "#fhir/Period{:id \"212329\"}")))


(deftest identifier-test
  (testing "type"
    (is (= :fhir/Identifier (type/type #fhir/Identifier{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Identifier{}
      "14336e1c"

      #fhir/Identifier{:id "id-130739"}
      "57c166f0"

      #fhir/Identifier{:extension [#fhir/Extension{}]}
      "810b77ff"

      #fhir/Identifier{:use #fhir/code"use-155144"}
      "4bf89602"

      #fhir/Identifier{:type #fhir/CodeableConcept{}}
      "736db874"

      #fhir/Identifier{:system #fhir/uri"system-145514"}
      "acbabb5d"

      #fhir/Identifier{:value "value-145509"}
      "de7e521f"

      #fhir/Identifier{:period #fhir/Period{}}
      "8a73bfa3"

      #fhir/Identifier{:assigner #fhir/Reference{}}
      "aa994e1e"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Identifier{}
      []))

  (testing "instance size"
    (is (= 64 (total-size #fhir/Identifier{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Identifier{} "#fhir/Identifier{}"
      #fhir/Identifier{:id "212329"} "#fhir/Identifier{:id \"212329\"}")))


(deftest reference-test
  (testing "type"
    (is (= :fhir/Reference (type/type #fhir/Reference{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Reference{}
      "6498613c"

      #fhir/Reference{:id "id-130802"}
      "a48cca5a"

      #fhir/Reference{:extension [#fhir/Extension{}]}
      "210e3eb7"

      #fhir/Reference{:reference "Patient/0"}
      "cd80b8ac"

      #fhir/Reference{:type #fhir/uri"type-161222"}
      "2fe271cd"

      #fhir/Reference{:identifier #fhir/Identifier{}}
      "eb066d27"

      #fhir/Reference{:display "display-161314"}
      "543cf75f"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Reference{}
      []

      #fhir/Reference{:extension [#fhir/Extension{}]}
      []

      #fhir/Reference
          {:extension
           [#fhir/Extension{:value #fhir/Reference{:reference "Patient/1"}}]}
      [["Patient" "1"]]

      #fhir/Reference{:reference "Patient/0"}
      [["Patient" "0"]]

      #fhir/Reference
          {:extension
           [#fhir/Extension{:value #fhir/Reference{:reference "Patient/1"}}]
           :reference "Patient/0"}
      [["Patient" "0"] ["Patient" "1"]]))

  (testing "instance size"
    (is (= 56 (total-size #fhir/Reference{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Reference{} "#fhir/Reference{}"
      #fhir/Reference{:id "212329"} "#fhir/Reference{:id \"212329\"}")))


(deftest meta-test
  (testing "type"
    (is (= :fhir/Meta (type/type #fhir/Meta{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/Meta{}
      "cbae28fd"

      #fhir/Meta{:id "id-130825"}
      "c2c18a00"

      #fhir/Meta{:extension [#fhir/Extension{}]}
      "aaf41f94"

      #fhir/Meta{:versionId #fhir/id"versionId-161415"}
      "9edaa9b"

      (type/map->Meta {:lastUpdated Instant/EPOCH})
      "38b8dfe3"

      #fhir/Meta{:source #fhir/uri"source-161629"}
      "bc99bc82"

      #fhir/Meta{:profile [#fhir/canonical"profile-uri-145024"]}
      "b13c3d52"

      #fhir/Meta{:security [#fhir/Coding{}]}
      "9b7633bc"

      #fhir/Meta{:tag [#fhir/Coding{}]}
      "96e4e336"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/Meta{}
      []

      #fhir/Meta{:extension [#fhir/Extension{}]}
      []

      #fhir/Meta
          {:extension
           [#fhir/Extension{:value #fhir/Reference{:reference "Patient/2"}}]}
      [["Patient" "2"]]))

  (testing "instance size"
    (is (= 64 (total-size #fhir/Meta{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/Meta{} "#fhir/Meta{}"
      #fhir/Meta{:id "212329"} "#fhir/Meta{:id \"212329\"}")))


(deftest bundle-entry-search-test
  (testing "type"
    (is (= :fhir.Bundle.entry/search (type/type #fhir/BundleEntrySearch{}))))

  (testing "hash-into"
    (are [x hex] (= hex (murmur3 x))
      #fhir/BundleEntrySearch{}
      "f945531f"

      #fhir/BundleEntrySearch{:id "id-130825"}
      "6b1b9201"

      #fhir/BundleEntrySearch{:extension [#fhir/Extension{}]}
      "f24daf4f"

      #fhir/BundleEntrySearch{:mode #fhir/code"match"}
      "5912b48c"

      #fhir/BundleEntrySearch{:score 1M}
      "2b2509dc"))

  (testing "references"
    (are [x refs] (= refs (type/references x))
      #fhir/BundleEntrySearch{}
      []))

  (testing "instance size"
    (is (= 48 (total-size #fhir/BundleEntrySearch{}))))

  (testing "print"
    (are [v s] (= s (pr-str v))
      #fhir/BundleEntrySearch{} "#fhir/BundleEntrySearch{}"
      #fhir/BundleEntrySearch{:id "212329"} "#fhir/BundleEntrySearch{:id \"212329\"}")))
