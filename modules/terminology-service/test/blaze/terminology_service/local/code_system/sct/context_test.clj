(ns blaze.terminology-service.local.code-system.sct.context-test
  (:require
   [blaze.fhir.test-util]
   [blaze.path :refer [path]]
   [blaze.path-spec]
   [blaze.terminology-service.local.code-system.sct.context :as context]
   [blaze.terminology-service.local.code-system.sct.context-spec]
   [blaze.test-util :as tu]
   [clojure.java.io :as io]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [are deftest is testing]]
   [cognitect.anomalies :as anom]
   [juxt.iota :refer [given]])
  (:import [java.nio.file Path]))

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
  (let [module-dependency-index {11000274103 (time-map
                                              20231115 [[900000000000207008 20231001]
                                                        [900000000000012004 20231001]]
                                              20240415 [[900000000000207008 20240401]])}
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
        (is (nil? (find-concept 11000274103 20240515 38945723498756))))

      (testing "concept from core module"
        (testing "is active"
          (is (true? (find-concept 11000274103 20231115 106004))))

        (testing "is inactive"
          (is (false? (find-concept 11000274103 20240515 106004)))))

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
                                                20020131 {true [100000000]})}}
          neighbors (partial context/neighbors {} index)]

      (testing "neighbor did not exist at this time"
        (is (= #{} (neighbors 900000000000207008 20020130 102272007))))

      (testing "neighbor just became active"
        (is (= #{100000000} (neighbors 900000000000207008 20020131 102272007))))

      (testing "neighbor is still active"
        (is (= #{100000000} (neighbors 900000000000207008 20090730 102272007))))

      (testing "neighbor just became inactive"
        (is (= #{} (neighbors 900000000000207008 20090731 102272007))))

      (testing "neighbor is still inactive"
        (is (= #{} (neighbors 900000000000207008 20241216 102272007))))))

  (testing "two neighbors"
    (let [index {900000000000207008 {102272007 (time-map
                                                20090731 {false [100001001]}
                                                20030531 {false [100000000] true [100001001]}
                                                20020131 {true [100000000]})}}
          neighbors (partial context/neighbors {} index)]

      (testing "neighbor 100000000 did not exist at this time"
        (is (= #{} (neighbors 900000000000207008 20020130 102272007))))

      (testing "neighbor 100000000 just became active"
        (is (= #{100000000} (neighbors 900000000000207008 20020131 102272007))))

      (testing "neighbor 100000000 is still active"
        (is (= #{100000000} (neighbors 900000000000207008 20030530 102272007))))

      (testing "neighbor 100001001 just became active"
        (is (= #{100001001} (neighbors 900000000000207008 20030531 102272007))))

      (testing "neighbor 100001001 is still active"
        (is (= #{100001001} (neighbors 900000000000207008 20090730 102272007))))

      (testing "neighbor 100001001 just became inactive"
        (is (= #{} (neighbors 900000000000207008 20090731 102272007))))

      (testing "neighbor is still inactive"
        (is (= #{} (neighbors 900000000000207008 20241216 102272007)))))))

(deftest transitive-neighbors-test
  (let [index {900000000000207008 {102272007 (time-map
                                              20090731 {false [100000000]}
                                              20020131 {true [100000000]})
                                   100000000 (time-map
                                              20090731 {false [100001001]}
                                              20020131 {true [100001001]})}}
        transitive-neighbors (partial context/transitive-neighbors {} index)]
    (is (= #{100000000 100001001} (transitive-neighbors 900000000000207008 20020131 102272007)))))

(deftest transitive-neighbors-including-test
  (let [index {900000000000207008 {102272007 (time-map
                                              20090731 {false [100000000]}
                                              20020131 {true [100000000]})
                                   100000000 (time-map
                                              20090731 {false [100001001]}
                                              20020131 {true [100001001]})}}
        transitive-neighbors-including (partial context/transitive-neighbors-including {} index)]
    (is (= #{100000000 100001001 102272007} (transitive-neighbors-including 900000000000207008 20020131 102272007)))))

(deftest find-transitive-neighbor-test
  (let [index {900000000000207008 {102272007 (time-map
                                              20090731 {false [100000000]}
                                              20020131 {true [100000000]})
                                   100000000 (time-map
                                              20090731 {false [100001001]}
                                              20020131 {true [100001001]})}}
        find-transitive-neighbor (partial context/find-transitive-neighbor {} index)]
    (are [concept-id] (true? (find-transitive-neighbor 900000000000207008 20020131 102272007 concept-id))
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

(defn- resource-path [name]
  (Path/of (.toURI (io/resource name))))

(defn read-synonym-index [concept-id]
  (context/stream-file
   context/build-synonym-index
   (resource-path (format "sct_%d/sct2_Description_Full_GermanyEdition_20241115.txt" concept-id))))

(deftest build-synonym-index-test
  (is (= (context/build-synonym-index
          #_(context/build-acceptability-index
             (.stream
              ["89967345-b4d5-5b0e-9e36-d76fc2aeef98\t20090131\t1\t900000000000207008\t900000000000508004\t2792601011\t900000000000548007"
               "89967345-b4d5-5b0e-9e36-d76fc2aeef98\t20180131\t1\t900000000000207008\t900000000000508004\t2792601011\t900000000000549004"
               "d857eb70-d7db-5fd8-9671-c38fbcc26790\t20090131\t1\t900000000000207008\t900000000000509007\t2792601011\t900000000000548007"
               "d857eb70-d7db-5fd8-9671-c38fbcc26790\t20180131\t1\t900000000000207008\t900000000000509007\t2792601011\t900000000000549004"
               "e0add9c1-13dc-470a-86f8-cdfe6ec7da88\t20180131\t1\t900000000000207008\t900000000000509007\t3533707010\t900000000000548007"
               "6e2d2ba3-eac0-4c4e-b97a-cb81273adc84\t20180131\t1\t900000000000207008\t900000000000508004\t3533707010\t900000000000548007"
               "7879ca96-40ff-b93d-ea8f-826402e5690f\t20241115\t1\t11000274103\t31000274107\t771861000274114\t900000000000548007"
               "2cd6d999-c60a-ea0b-e741-4c8af71f2ac1\t20241115\t1\t11000274103\t31000274107\t810131000274113\t900000000000549004"]))
          (.stream
           ["2792601011\t20090131\t1\t900000000000207008\t440500007\ten\t900000000000013009\tBlood spot specimen\t900000000000017005"
            "2792601011\t20180131\t1\t900000000000207008\t440500007\ten\t900000000000013009\tBlood spot specimen\t900000000000448009"
            "3533707010\t20180131\t1\t900000000000207008\t440500007\ten\t900000000000013009\tDried blood spot specimen\t900000000000448009"
            "771861000274114\t20241115\t1\t11000274103\t440500007\tde\t900000000000013009\tTrockenblutkarte\t900000000000017005"
            "810131000274113\t20241115\t1\t11000274103\t440500007\tde\t900000000000013009\tDried Blood Spot Sample\t900000000000017005"]))
         {900000000000207008
          {440500007
           {20090131
            {true [[2792601011 ["en" "Blood spot specimen"]]]}
            20180131
            {true [[2792601011 ["en" "Blood spot specimen"]]
                   [3533707010 ["en" "Dried blood spot specimen"]]]}}}
          11000274103
          {440500007
           {20241115
            {true [[771861000274114 ["de" "Trockenblutkarte"]]
                   [810131000274113 ["de" "Dried Blood Spot Sample"]]]}}}}))

  (is (= (context/build-synonym-index
          #_(context/build-acceptability-index
             (.stream
              ["802cf339-8434-55dc-9ef8-5bc697b47fb3\t20100731\t1\t900000000000207008\t900000000000509007\t2871550018\t900000000000548007"
               "8b032d8a-ccd1-5112-a1c7-045b9a6ca32c\t20100731\t1\t900000000000207008\t900000000000508004\t2871550018\t900000000000548007"
               "a16db196-8f99-5a58-b325-6fd8ffd25f9f\t20100731\t1\t900000000000207008\t900000000000508004\t2871551019\t900000000000549004"
               "eae505f4-49a7-5afc-9a39-3f93acffeca7\t20100731\t1\t900000000000207008\t900000000000509007\t2871551019\t900000000000549004"
               "6931b37d-9868-5b10-befc-26cdd5abd306\t20100731\t1\t900000000000207008\t900000000000508004\t2871552014\t900000000000549004"
               "6eb8a64a-cba1-5168-b179-a2485fa7aef7\t20100731\t1\t900000000000207008\t900000000000509007\t2871552014\t900000000000549004"]))
          (.stream
           ["2871550018\t20100731\t1\t900000000000207008\t445295009\ten\t900000000000013009\tBlood specimen with EDTA\t900000000000020002"
            "2871551019\t20100731\t1\t900000000000207008\t445295009\ten\t900000000000013009\tBlood specimen with edetic acid\t900000000000020002"
            "2871551019\t20170731\t1\t900000000000207008\t445295009\ten\t900000000000013009\tBlood specimen with edetic acid\t900000000000448009"
            "2871552014\t20100731\t1\t900000000000207008\t445295009\ten\t900000000000013009\tBlood specimen with ethylenediamine tetraacetic acid\t900000000000020002"
            "2871552014\t20170731\t1\t900000000000207008\t445295009\ten\t900000000000013009\tBlood specimen with ethylenediamine tetraacetic acid\t900000000000448009"]))
         {900000000000207008
          {445295009
           {20100731
            {true [[2871550018 ["en" "Blood specimen with EDTA"]]
                   [2871551019 ["en" "Blood specimen with edetic acid"]]
                   [2871552014 ["en" "Blood specimen with ethylenediamine tetraacetic acid"]]]}
            20170731
            {true [[2871551019 ["en" "Blood specimen with edetic acid"]]
                   [2871552014 ["en" "Blood specimen with ethylenediamine tetraacetic acid"]]]}}}}))

  (is (= (read-synonym-index 73212002)
         {900000000000207008
          {73212002
           {20020131
            {true [[121591019 ["en" "Iodipamide"]]
                   [502373013 ["en" "Adipiodone"]]]}
            20020731
            {true [[1236493018 ["en" "Adipiodone"]]
                   [1237058014 ["en" "Iodipamide"]]]}
            20030731
            {false [[502373013 ["en" "Adipiodone"]]
                    [1237058014 ["en" "Iodipamide"]]]}
            20040131
            {false [[121591019 ["en" "Iodipamide"]]
                    [1236493018 ["en" "Adipiodone"]]]
             true [[2154314016 ["en" "Adipiodone"]]
                   [2155335011 ["en" "Iodipamide"]]]}
            20170731
            {true [[2154314016 ["en" "Adipiodone"]]
                   [2155335011 ["en" "Iodipamide"]]]}
            20180131
            {false [[2154314016 ["en" "Adipiodone"]]]
             true [[3522511019 ["en" "Product containing iodipamide"]]]}
            20180731
            {false [[2155335011 ["en" "Iodipamide"]]]
             true [[3669346012 ["en" "Iodipamide product"]]]}
            20190131
            {false [[3522511019 ["en" "Product containing iodipamide"]]
                    [3669346012 ["en" "Iodipamide product"]]]
             true [[3715325011 ["en" "Iodipamide-containing product"]]]}}}}))

  (is (= (read-synonym-index 309051001)
         {900000000000207008
          {309051001
           {20020131
            {true [[452384015 ["en" "Body fluid sample"]]]}
            20040731
            {true [[2476472013 ["en" "Body fluid specimen"]]]}
            20170731
            {true [[452384015 ["en" "Body fluid sample"]]
                   [2476472013 ["en" "Body fluid specimen"]]]}}}
          11000274103
          {309051001
           {20241115
            {true [[800431000274118 ["de" "Körperflüssigkeitsprobe"]]]}}}})))

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
  (let [module-dependency-index {11000274103 (time-map 20241115 [[900000000000207008 20241001]])}
        index {900000000000207008
               {440500007
                (time-map
                 20090131
                 {true [[2792601011 ["en" "Blood spot specimen"]]]}
                 20180131
                 {true [[2792601011 ["en" "Blood spot specimen"]]
                        [3533707010 ["en" "Dried blood spot specimen"]]]})}
               11000274103
               {440500007
                (time-map
                 20241115
                 {true [[771861000274114 ["de" "Trockenblutkarte"]]
                        [810131000274113 ["de" "Dried Blood Spot Sample"]]]})}}
        find-synonyms (partial context/find-synonyms module-dependency-index index)]

    (testing "synonyms did not exist at this time"
      (is (empty? (find-synonyms 900000000000207008 20080731 440500007))))

    (testing "one synonym became active"
      (is (= [[2792601011 ["en" "Blood spot specimen"]]]
             (find-synonyms 900000000000207008 20090131 440500007))))

    (testing "a second synonym became active"
      (is (= [[2792601011 ["en" "Blood spot specimen"]]
              [3533707010 ["en" "Dried blood spot specimen"]]]
             (find-synonyms 900000000000207008 20180131 440500007))))

    (testing "Germany concept has also the international synonyms"
      (is (= [[771861000274114 ["de" "Trockenblutkarte"]]
              [810131000274113 ["de" "Dried Blood Spot Sample"]]
              [2792601011 ["en" "Blood spot specimen"]]
              [3533707010 ["en" "Dried blood spot specimen"]]]
             (find-synonyms 11000274103 20241116 440500007)))))

  (let [index {900000000000207008
               {445295009
                (time-map
                 20100731
                 {true [[2871550018 ["en" "Blood specimen with EDTA"]]
                        [2871551019 ["en" "Blood specimen with edetic acid"]]
                        [2871552014 ["en" "Blood specimen with ethylenediamine tetraacetic acid"]]]}
                 20170731
                 {true [[2871551019 ["en" "Blood specimen with edetic acid"]]
                        [2871552014 ["en" "Blood specimen with ethylenediamine tetraacetic acid"]]]})}}
        find-synonyms (partial context/find-synonyms {} index)]

    (testing "one synonym became active"
      (is (= [[2871550018 ["en" "Blood specimen with EDTA"]]
              [2871551019 ["en" "Blood specimen with edetic acid"]]
              [2871552014 ["en" "Blood specimen with ethylenediamine tetraacetic acid"]]]
             (find-synonyms 900000000000207008 20100731 445295009))))

    (testing "the first synonym is still active"
      (is (= [[2871550018 ["en" "Blood specimen with EDTA"]]
              [2871551019 ["en" "Blood specimen with edetic acid"]]
              [2871552014 ["en" "Blood specimen with ethylenediamine tetraacetic acid"]]]
             (find-synonyms 900000000000207008 20170731 445295009)))))

  (let [index (read-synonym-index 73212002)
        find-synonyms (partial context/find-synonyms {} index)]

    (testing "20020131"
      (is (= [[121591019 ["en" "Iodipamide"]]
              [502373013 ["en" "Adipiodone"]]]
             (find-synonyms 900000000000207008 20020131 73212002))))

    (testing "20020731"
      (is (= [[121591019 ["en" "Iodipamide"]]
              [502373013 ["en" "Adipiodone"]]
              [1236493018 ["en" "Adipiodone"]]
              [1237058014 ["en" "Iodipamide"]]]
             (find-synonyms 900000000000207008 20020731 73212002))))

    (testing "20030731"
      (is (= [[121591019 ["en" "Iodipamide"]]
              [1236493018 ["en" "Adipiodone"]]]
             (find-synonyms 900000000000207008 20030731 73212002))))

    (testing "20040131"
      (is (= [[2154314016 ["en" "Adipiodone"]]
              [2155335011 ["en" "Iodipamide"]]]
             (find-synonyms 900000000000207008 20040131 73212002))))

    (testing "20170731"
      (is (= [[2154314016 ["en" "Adipiodone"]]
              [2155335011 ["en" "Iodipamide"]]]
             (find-synonyms 900000000000207008 20170731 73212002))))

    (testing "20180131"
      (is (= [[2155335011 ["en" "Iodipamide"]]
              [3522511019 ["en" "Product containing iodipamide"]]]
             (find-synonyms 900000000000207008 20180131 73212002))))

    (testing "20180731"
      (is (= [[3522511019 ["en" "Product containing iodipamide"]]
              [3669346012 ["en" "Iodipamide product"]]]
             (find-synonyms 900000000000207008 20180731 73212002))))

    (testing "20190131"
      (is (= [[3715325011 ["en" "Iodipamide-containing product"]]]
             (find-synonyms 900000000000207008 20190131 73212002))))))

(deftest build-test
  (testing "wrong path"
    (given (context/build (path "src"))
      ::anom/category := ::anom/not-found
      ::anom/message := "Can't find a file starting with `Full` in `src`."))

  (testing "correct path"
    (given (context/build (path "sct-release"))
      [:code-systems count] := 25
      [:code-systems 0 :fhir/type] := :fhir/CodeSystem
      [:code-systems 0 :url] := #fhir/uri "http://snomed.info/sct"
      [:code-systems 0 :version] := #fhir/string "http://snomed.info/sct/11000274103/version/20231115"
      [:code-systems 0 :title] := #fhir/string "Germany National Extension module (core metadata concept)"
      [:code-systems 0 :status] := #fhir/code "active"
      [:code-systems 0 :experimental] := #fhir/boolean false
      [:code-systems 0 :date] := #fhir/dateTime "2023-11-15"
      [:code-systems 0 :caseSensitive] := #fhir/boolean true
      [:code-systems 0 :hierarchyMeaning] := #fhir/code "is-a"
      [:code-systems 0 :versionNeeded] := #fhir/boolean false
      [:code-systems 0 :content] := #fhir/code "not-present"
      [:code-systems 0 :filter count] := 2
      [:code-systems 0 :filter 0 :code] := #fhir/code "concept"
      [:code-systems 0 :filter 0 :description] := #fhir/string "Includes all concept ids that have a transitive is-a relationship with the code provided as the value."
      [:code-systems 0 :filter 0 :operator count] := 1
      [:code-systems 0 :filter 0 :operator 0] := #fhir/code "is-a"
      [:code-systems 0 :filter 0 :value] := #fhir/string "A SNOMED CT code"
      [:code-systems 0 :filter 1 :code] := #fhir/code "concept"
      [:code-systems 0 :filter 1 :description] := #fhir/string "Includes all concept ids that have a transitive is-a relationship with the code provided as the value, excluding the code itself."
      [:code-systems 0 :filter 1 :operator count] := 1
      [:code-systems 0 :filter 1 :operator 0] := #fhir/code "descendent-of"
      [:code-systems 0 :filter 1 :value] := #fhir/string "A SNOMED CT code"

      [:code-systems 1 :version] := #fhir/string "http://snomed.info/sct/11000274103/version/20240515"
      [:code-systems 1 :title] := #fhir/string "Germany National Extension module (core metadata concept)"
      [:code-systems 1 :date] := #fhir/dateTime "2024-05-15"

      [:code-systems 2 :version] := #fhir/string "http://snomed.info/sct/11000274103/version/20241115"
      [:code-systems 2 :title] := #fhir/string "Germany National Extension module (core metadata concept)"
      [:code-systems 2 :date] := #fhir/dateTime "2024-11-15"

      [:code-systems 3 :version] := #fhir/string "http://snomed.info/sct/900000000000207008/version/20220131"
      [:code-systems 3 :title] := #fhir/string "SNOMED CT core module (core metadata concept)"
      [:code-systems 3 :date] := #fhir/dateTime "2022-01-31"

      [:current-int-system :date] := #fhir/dateTime "2024-10-01")))
