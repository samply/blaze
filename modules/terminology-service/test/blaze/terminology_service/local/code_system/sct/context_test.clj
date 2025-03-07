(ns blaze.terminology-service.local.code-system.sct.context-test
  (:require
   [blaze.fhir.test-util]
   [blaze.path :refer [path]]
   [blaze.path-spec]
   [blaze.terminology-service.local.code-system.sct.context :as context]
   [blaze.terminology-service.local.code-system.sct.context-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]]))

(set! *warn-on-reflection* true)
(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest build-concept-index-test
  (is (= (context/build-concept-index
          (.stream
           ["106004\t20020131\t1\t900000000000207008\t900000000000074008"
            "106004\t20240301\t0\t900000000000207008\t900000000000074008"]))
         {900000000000207008 {106004 {20240301 false
                                      20020131 true}}})))

(deftest build-module-dependency-index-test
  (is (= (context/build-module-dependency-index
          (.stream
           ["6d6d2700-658a-b414-33b5-2c1d24f3cf8a\t20231115\t1\t11000274103\t900000000000534007\t900000000000207008\t20231115\t20231001"
            "6d6d2700-658a-b414-33b5-2c1d24f3cf8a\t20240115\t1\t11000274103\t900000000000534007\t900000000000207008\t20240115\t20240101"
            "b3f5eb17-831f-11ee-bd1c-38f3ab70978c\t20231115\t1\t11000274103\t900000000000534007\t900000000000012004\t20231115\t20231001"
            "1244116f-fdb5-5645-afcc-5281288409da\t20020131\t1\t900000000000207008\t900000000000534007\t900000000000012004\t20020131\t20020131"]))
         {11000274103 {20231115 [[900000000000207008 20231001]
                                 [900000000000012004 20231001]]
                       20240115 [[900000000000207008 20240101]]}
          900000000000207008 {20020131 [[900000000000012004 20020131]]}})))

(defn- time-map [& kvs]
  (apply sorted-map-by > kvs))

(deftest find-concept-test
  (let [module-dependency-index {11000274103 {20231115 [[900000000000207008 20231001]
                                                        [900000000000012004 20231001]]
                                              20240415 [[900000000000207008 20240401]]}}
        concept-index {900000000000207008 {106004
                                           (time-map
                                            20240301 false
                                            20020131 true)}
                       900000000000012004 {900000000000012004
                                           (time-map 20020131 true)}}
        find-concept (partial context/find-concept module-dependency-index concept-index)]

    (testing "from core module"
      (testing "concept did not exist at this time"
        (is (nil? (find-concept 900000000000207008 20020130 106004))))

      (testing "concept just became active"
        (is (true? (find-concept 900000000000207008 20020131 106004))))

      (testing "concept is still active"
        (is (true? (find-concept 900000000000207008 20240229 106004))))

      (testing "concept just became inactive"
        (is (false? (find-concept 900000000000207008 20240301 106004))))

      (testing "concept is still inactive"
        (is (false? (find-concept 900000000000207008 20241216 106004)))))

    (testing "from Germany National Extension module"
      (testing "non-existing concept"
        (is (nil? (find-concept 11000274103 20240415 38945723498756))))

      (testing "concept from core module"
        (testing "is active"
          (is (true? (find-concept 11000274103 20231115 106004))))

        (testing "is inactive"
          (is (false? (find-concept 11000274103 20240415 106004)))))

      (testing "concept from model component module"
        (is (true? (find-concept 11000274103 20231115 900000000000012004)))))))

(deftest build-parent-index-test
  (is (= (context/build-parent-index
          (.stream
           ["100022\t20020131\t1\t900000000000207008\t100000000\t102272007\t0\t116680003\t900000000000011006\t900000000000451002"
            "100022\t20090731\t0\t900000000000207008\t100000000\t102272007\t0\t116680003\t900000000000011006\t900000000000451002"
            "104029\t20020131\t1\t900000000000207008\t100001001\t102272007\t0\t116680003\t900000000000011006\t900000000000451002"
            "104029\t20090731\t0\t900000000000207008\t100001001\t102272007\t0\t116680003\t900000000000011006\t900000000000451002"]))
         {900000000000207008 {100000000 {20090731 {false [102272007]}
                                         20020131 {true [102272007]}}
                              100001001 {20090731 {false [102272007]}
                                         20020131 {true [102272007]}}}})))

(deftest build-child-index-test
  (is (= (context/build-child-index
          (.stream
           ["100022\t20020131\t1\t900000000000207008\t100000000\t102272007\t0\t116680003\t900000000000011006\t900000000000451002"
            "100022\t20090731\t0\t900000000000207008\t100000000\t102272007\t0\t116680003\t900000000000011006\t900000000000451002"
            "104029\t20020131\t1\t900000000000207008\t100001001\t102272007\t0\t116680003\t900000000000011006\t900000000000451002"
            "104029\t20090731\t0\t900000000000207008\t100001001\t102272007\t0\t116680003\t900000000000011006\t900000000000451002"]))
         {900000000000207008 {102272007 {20090731 {false [100000000 100001001]}
                                         20020131 {true [100000000 100001001]}}}})))

(deftest neighbors-test
  (testing "one neighbor"
    (let [index {900000000000207008 {102272007 (time-map
                                                20090731 {false [100000000]}
                                                20020131 {true [100000000]})}}]

      (testing "neighbor did not exist at this time"
        (is (= #{} (context/neighbors index 900000000000207008 20020130 102272007))))

      (testing "neighbor just became active"
        (is (= #{100000000} (context/neighbors index 900000000000207008 20020131 102272007))))

      (testing "neighbor is still active"
        (is (= #{100000000} (context/neighbors index 900000000000207008 20090730 102272007))))

      (testing "neighbor just became inactive"
        (is (= #{} (context/neighbors index 900000000000207008 20090731 102272007))))

      (testing "neighbor is still inactive"
        (is (= #{} (context/neighbors index 900000000000207008 20241216 102272007))))))

  (testing "two neighbors"
    (let [index {900000000000207008 {102272007 (time-map
                                                20090731 {false [100001001]}
                                                20030531 {false [100000000] true [100001001]}
                                                20020131 {true [100000000]})}}]

      (testing "neighbor 100000000 did not exist at this time"
        (is (= #{} (context/neighbors index 900000000000207008 20020130 102272007))))

      (testing "neighbor 100000000 just became active"
        (is (= #{100000000} (context/neighbors index 900000000000207008 20020131 102272007))))

      (testing "neighbor 100000000 is still active"
        (is (= #{100000000} (context/neighbors index 900000000000207008 20030530 102272007))))

      (testing "neighbor 100001001 just became active"
        (is (= #{100001001} (context/neighbors index 900000000000207008 20030531 102272007))))

      (testing "neighbor 100001001 is still active"
        (is (= #{100001001} (context/neighbors index 900000000000207008 20090730 102272007))))

      (testing "neighbor 100001001 just became inactive"
        (is (= #{} (context/neighbors index 900000000000207008 20090731 102272007))))

      (testing "neighbor is still inactive"
        (is (= #{} (context/neighbors index 900000000000207008 20241216 102272007)))))))

(deftest transitive-neighbors-test
  (let [index {900000000000207008 {102272007 (time-map
                                              20090731 {false [100000000]}
                                              20020131 {true [100000000]})
                                   100000000 (time-map
                                              20090731 {false [100001001]}
                                              20020131 {true [100001001]})}}]
    (is (= #{100000000 100001001} (context/transitive-neighbors index 900000000000207008 20020131 102272007)))))

(deftest transitive-neighbors-including-test
  (let [index {900000000000207008 {102272007 (time-map
                                              20090731 {false [100000000]}
                                              20020131 {true [100000000]})
                                   100000000 (time-map
                                              20090731 {false [100001001]}
                                              20020131 {true [100001001]})}}]
    (is (= #{100000000 100001001 102272007} (context/transitive-neighbors-including index 900000000000207008 20020131 102272007)))))

(deftest find-transitive-neighbor-test
  (let [index {900000000000207008 {102272007 (time-map
                                              20090731 {false [100000000]}
                                              20020131 {true [100000000]})
                                   100000000 (time-map
                                              20090731 {false [100001001]}
                                              20020131 {true [100001001]})}}]
    (are [concept-id] (true? (context/find-transitive-neighbor index 900000000000207008 20020131 102272007 concept-id))
      100000000 100001001)))

(deftest build-fully-specified-name-index-test
  (is (= (context/build-fully-specified-name-index
          (.stream
           ["1145019\t20020131\t1\t900000000000207008\t127858008\ten\t900000000000003001\tEctopic female breast (body structure)\t900000000000020002"
            "1145019\t20040731\t0\t900000000000207008\t127858008\ten\t900000000000003001\tEctopic female breast (body structure)\t900000000000020002"
            "2461362011\t20040731\t1\t900000000000207008\t127858008\ten\t900000000000003001\tEctopic female breast (morphologic abnormality)\t900000000000020002"
            "2461362011\t20170731\t1\t900000000000207008\t127858008\ten\t900000000000003001\tEctopic female breast (morphologic abnormality)\t900000000000448009"]))
         {900000000000207008
          {127858008
           {20170731 {true "Ectopic female breast (morphologic abnormality)"}
            20040731 {false "Ectopic female breast (body structure)"
                      true "Ectopic female breast (morphologic abnormality)"}
            20020131 {true "Ectopic female breast (body structure)"}}}})))

(deftest build-synonym-index-test
  (is (= (context/build-synonym-index
          (.stream
           ["1778924019\t20030731\t1\t900000000000207008\t399537006\ten\t900000000000013009\tClinical TNM stage grouping\t900000000000020002"
            "1786868015\t20030731\t1\t900000000000207008\t399537006\ten\t900000000000013009\tcTNM stage grouping\t900000000000017005"]))
         {900000000000207008
          {399537006
           {20030731
            {true [["en" "Clinical TNM stage grouping"] ["en" "cTNM stage grouping"]]}}}})))

(deftest find-fully-specified-name-test
  (let [index {900000000000207008
               {127858008
                (time-map
                 20170731 {false "Ectopic female breast (morphologic abnormality)"}
                 20040731 {false "Ectopic female breast (body structure)"
                           true "Ectopic female breast (morphologic abnormality)"}
                 20020131 {true "Ectopic female breast (body structure)"})}}
        find-fully-specified-name (partial context/find-fully-specified-name {} index)]

    (testing "description did not exist at this time"
      (is (nil? (find-fully-specified-name 900000000000207008 20020130 127858008))))

    (testing "description just became active"
      (is (= "Ectopic female breast (body structure)" (find-fully-specified-name 900000000000207008 20020131 127858008))))

    (testing "description is still the first variant"
      (is (= "Ectopic female breast (body structure)" (find-fully-specified-name 900000000000207008 20040730 127858008))))

    (testing "description changed"
      (is (= "Ectopic female breast (morphologic abnormality)" (find-fully-specified-name 900000000000207008 20040731 127858008))))

    (testing "description is still the second variant"
      (is (= "Ectopic female breast (morphologic abnormality)" (find-fully-specified-name 900000000000207008 20170730 127858008))))

    (testing "description just became inactive"
      (is (nil? (find-fully-specified-name 900000000000207008 20170731 127858008))))))

(deftest find-synonyms-test
  ;; TODO: use real history
  (let [index {900000000000207008
               {399537006
                (time-map
                 20030731 {true [["en" "Clinical TNM stage grouping"]
                                 ["en" "cTNM stage grouping"]]})}}
        find-synonyms (partial context/find-synonyms {} index)]

    (testing "description did not exist at this time"
      (is (empty? (find-synonyms 900000000000207008 20030730 399537006))))

    (testing "description just became active"
      (is (= [["en" "Clinical TNM stage grouping"]
              ["en" "cTNM stage grouping"]] (find-synonyms 900000000000207008 20030731 399537006))))))

(deftest build-test
  (testing "wrong path"
    (given (context/build (path "src"))
      ::anom/category := ::anom/not-found
      ::anom/message := "Can't find a file starting with `Full` in `src`."))

  (testing "correct path"
    (given (context/build (path "sct-release"))
      [:code-systems count] := 136
      [:code-systems 0 :fhir/type] := :fhir/CodeSystem
      [:code-systems 0 :url] := #fhir/uri"http://snomed.info/sct"
      [:code-systems 0 :version] := #fhir/string"http://snomed.info/sct/11000274103/version/20231115"
      [:code-systems 0 :title] := #fhir/string"Germany National Extension module (core metadata concept)"
      [:code-systems 0 :status] := #fhir/code"active"
      [:code-systems 0 :experimental] := #fhir/boolean false
      [:code-systems 0 :date] := #fhir/dateTime"2023-11-15"
      [:code-systems 0 :caseSensitive] := #fhir/boolean true
      [:code-systems 0 :hierarchyMeaning] := #fhir/code"is-a"
      [:code-systems 0 :versionNeeded] := #fhir/boolean false
      [:code-systems 0 :content] := #fhir/code"not-present"
      [:code-systems 0 :filter count] := 2
      [:code-systems 0 :filter 0 :code] := #fhir/code"concept"
      [:code-systems 0 :filter 0 :description] := #fhir/string"Includes all concept ids that have a transitive is-a relationship with the code provided as the value."
      [:code-systems 0 :filter 0 :operator count] := 1
      [:code-systems 0 :filter 0 :operator 0] := #fhir/code"is-a"
      [:code-systems 0 :filter 0 :value] := #fhir/string"A SNOMED CT code"
      [:code-systems 0 :filter 1 :code] := #fhir/code"concept"
      [:code-systems 0 :filter 1 :description] := #fhir/string"Includes all concept ids that have a transitive is-a relationship with the code provided as the value, excluding the code itself."
      [:code-systems 0 :filter 1 :operator count] := 1
      [:code-systems 0 :filter 1 :operator 0] := #fhir/code"descendent-of"
      [:code-systems 0 :filter 1 :value] := #fhir/string"A SNOMED CT code"

      [:code-systems 1 :version] := #fhir/string"http://snomed.info/sct/11000274103/version/20240115"
      [:code-systems 1 :title] := #fhir/string"Germany National Extension module (core metadata concept)"
      [:code-systems 1 :date] := #fhir/dateTime"2024-01-15"

      [:code-systems 2 :version] := #fhir/string"http://snomed.info/sct/11000274103/version/20240415"
      [:code-systems 2 :title] := #fhir/string"Germany National Extension module (core metadata concept)"
      [:code-systems 2 :date] := #fhir/dateTime"2024-04-15"

      [:code-systems 3 :version] := #fhir/string"http://snomed.info/sct/11000274103/version/20240711"
      [:code-systems 3 :title] := #fhir/string"Germany National Extension module (core metadata concept)"
      [:code-systems 3 :date] := #fhir/dateTime"2024-07-11"

      [:code-systems 4 :version] := #fhir/string"http://snomed.info/sct/449080006/version/20130731"
      [:code-systems 4 :title] := #fhir/string"SNOMED CT to ICD-10 rule-based mapping module (core metadata concept)"
      [:code-systems 4 :date] := #fhir/dateTime"2013-07-31"

      [:code-systems 5 :version] := #fhir/string"http://snomed.info/sct/449080006/version/20140131"
      [:code-systems 5 :title] := #fhir/string"SNOMED CT to ICD-10 rule-based mapping module (core metadata concept)"
      [:code-systems 5 :date] := #fhir/dateTime"2014-01-31"

      [:current-int-system :date] := #fhir/dateTime"2024-10-01")))
