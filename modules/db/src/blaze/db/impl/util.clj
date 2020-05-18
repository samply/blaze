(ns blaze.db.impl.util
  (:refer-clojure :exclude [comp]))


(defn comp
  "Like `clojure.core/comp` but with more arities."
  ([] identity)
  ([f] f)
  ([f g]
   (fn
     ([] (f (g)))
     ([x] (f (g x)))
     ([x y] (f (g x y)))
     ([x y z] (f (g x y z)))
     ([x y z & args] (f (apply g x y z args)))))
  ([f g h]
   (fn
     ([] (f (g (h))))
     ([x] (f (g (h x))))
     ([x y] (f (g (h x y))))
     ([x y z] (f (g (h x y z))))
     ([x y z & args] (f (g (apply h x y z args))))))
  ([f g h i]
   (fn
     ([] (f (g (h (i)))))
     ([x] (f (g (h (i x)))))
     ([x y] (f (g (h (i x y)))))
     ([x y z] (f (g (h (i x y z)))))
     ([x y z & args] (f (g (h (apply i x y z args))))))))
