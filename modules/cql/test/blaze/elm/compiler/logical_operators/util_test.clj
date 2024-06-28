(ns blaze.elm.compiler.logical-operators.util-test
  (:require
   [blaze.elm.compiler.logical-operators.util :as u]
   [blaze.elm.expression.cache.bloom-filter :as bloom-filter]
   [blaze.elm.literal-spec]
   [blaze.test-util :as tu]
   [clojure.spec.test.alpha :as st]
   [clojure.test :as test :refer [deftest is testing]]))

(st/instrument)

(test/use-fixtures :each tu/fixture)

(deftest attach-cache-result-test
  (testing "empty input"
    (let [[tuples bfs] ((first (u/or-attach-cache-result identity nil)))]
      (is (empty? tuples))
      (is (empty? bfs))))

  (testing "with one triple"
    (testing "with direct Bloom filter"
      (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op [::bf] ::bf]])))]
        (testing "the direct Bloom filter of the triple is also the merged Bloom filter"
          (is (= [[::op ::bf]] tuples)))

        (testing "the Bloom filter is returned in the collection of all Bloom filters"
          (is (= [::bf] bfs)))))

    (testing "with one indirect Bloom filter"
      (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op [::bf]]])))]
        (testing "there is no merged Bloom filter"
          (is (= [[::op nil]] tuples)))

        (testing "the Bloom filter is returned in the collection of all Bloom filters"
          (is (= [::bf] bfs)))))

    (testing "with one direct and one indirect Bloom filter"
      (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op [::indirect-bf ::direct-bf] ::direct-bf]])))]
        (testing "the direct Bloom filter of the triple is also the merged Bloom filter"
          (is (= [[::op ::direct-bf]] tuples)))

        (testing "the Bloom filter is returned in the collection of all Bloom filters"
          (is (= [::indirect-bf ::direct-bf] bfs)))))

    (testing "with two indirect Bloom filters"
      (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op [::bf-1 ::bf-2]]])))]
        (testing "there is no merged Bloom filter"
          (is (= [[::op nil]] tuples)))

        (testing "the Bloom filters are returned in the collection of all Bloom filters"
          (is (= [::bf-1 ::bf-2] bfs))))))

  (testing "with two triples"
    (testing "only the second having a direct Bloom filter"
      (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op-1 [::bf-1]] [::op-2 [::bf-2] ::bf-2]])))]
        (testing "there is no merged Bloom filter on the first tuple"
          (is (= [[::op-1] [::op-2 ::bf-2]] tuples)))

        (testing "the indirect Bloom filter is also part of all Bloom filters"
          (is (= [::bf-1 ::bf-2] bfs)))))

    (testing "only the first having a direct Bloom filter"
      (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op-1 [::bf-1] ::bf-1] [::op-2 [::bf-2]]])))]
        (testing "there are no merged Bloom filters because we need the second having one"
          (is (= [[::op-1] [::op-2 nil]] tuples)))

        (testing "the indirect Bloom filter is also part of all Bloom filters"
          (is (= [::bf-1 ::bf-2] bfs)))))

    (testing "with both having direct Bloom filters"
      (with-redefs [bloom-filter/merge
                    (fn [bf-2 bf-1]
                      (assert (= ::bf-1 bf-1))
                      (assert (= ::bf-2 bf-2))
                      ::merged-bf)]
        (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op-1 [::bf-1] ::bf-1] [::op-2 [::bf-2] ::bf-2]])))]
          (testing "the first tuple has the merged Bloom filter while the second starts with the direct Bloom filter"
            (is (= [[::op-1 ::merged-bf] [::op-2 ::bf-2]] tuples)))

          (testing "the merged Bloom filter is not part of all Bloom filters"
            (is (= [::bf-1 ::bf-2] bfs)))))

      (testing "but merge returns nil"
        (with-redefs [bloom-filter/merge
                      (fn [bf-2 bf-1]
                        (assert (= ::bf-1 bf-1))
                        (assert (= ::bf-2 bf-2))
                        nil)]
          (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op-1 [::bf-1] ::bf-1] [::op-2 [::bf-2] ::bf-2]])))]
            (testing "there is no merged Bloom filter at the first tuple"
              (is (= [[::op-1 nil] [::op-2 ::bf-2]] tuples)))

            (testing "the merged Bloom filter is not part of all Bloom filters"
              (is (= [::bf-1 ::bf-2] bfs))))))))

  (testing "with three triples"
    (testing "with all having direct Bloom filters"
      (with-redefs [bloom-filter/merge
                    (fn [bf-a bf-b]
                      (cond
                        (and (= ::bf-3 bf-a) (= ::bf-2 bf-b))
                        ::bf-m1
                        (and (= ::bf-m1 bf-a) (= ::bf-1 bf-b))
                        ::bf-m2))]
        (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op-1 [::bf-1] ::bf-1] [::op-2 [::bf-2] ::bf-2] [::op-3 [::bf-3] ::bf-3]])))]
          (testing "the first two tuples have the merged Bloom filters while the last starts with the direct Bloom filter"
            (is (= [[::op-1 ::bf-m2] [::op-2 ::bf-m1] [::op-3 ::bf-3]] tuples)))

          (testing "the merged Bloom filter is not part of all Bloom filters"
            (is (= [::bf-1 ::bf-2 ::bf-3] bfs)))))

      (testing "merge returns nil on the second merge"
        (with-redefs [bloom-filter/merge
                      (fn [bf-a bf-b]
                        (cond
                          (and (= ::bf-3 bf-a) (= ::bf-2 bf-b))
                          ::bf-m1))]
          (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op-1 [::bf-1] ::bf-1] [::op-2 [::bf-2] ::bf-2] [::op-3 [::bf-3] ::bf-3]])))]
            (testing "there is no merged Bloom filter at the first tuple"
              (is (= [[::op-1 nil] [::op-2 ::bf-m1] [::op-3 ::bf-3]] tuples)))

            (testing "the merged Bloom filter is not part of all Bloom filters"
              (is (= [::bf-1 ::bf-2 ::bf-3] bfs))))))

      (testing "merge returns nil"
        (with-redefs [bloom-filter/merge (fn [_ _])]
          (let [[tuples bfs] ((first (u/or-attach-cache-result identity [[::op-1 [::bf-1] ::bf-1] [::op-2 [::bf-2] ::bf-2] [::op-3 [::bf-3] ::bf-3]])))]
            (testing "there are no merged Bloom filter at the first two tuples"
              (is (= [[::op-1] [::op-2 nil] [::op-3 ::bf-3]] tuples)))

            (testing "the merged Bloom filter is not part of all Bloom filters"
              (is (= [::bf-1 ::bf-2 ::bf-3] bfs)))))))))
