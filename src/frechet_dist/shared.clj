(ns frechet-dist.shared
  (:require [clojure.core.matrix :refer [get-row row-count mget mset!
                                         compute-matrix shape immutable]]))

(defn point-distance
  "computes the distance between all the possible point combinations of the two
  curves P and Q using the dist-fn"
  [P Q dist-fn]
  (immutable (compute-matrix :vectorz [(row-count P) (row-count Q)]
                             (fn [i j] (dist-fn (get-row P i) (get-row Q j))))))

(defn find-sequence
  "Given a point2point distance matrix CA find the path enclosed by the limits
  i-start j-start i-end j-end that minimizes the distance between the two
  curves from which CA was created."
  ([CA]
   (find-sequence CA (apply conj [0 0] (shape CA))))
  ([CA [i-start j-start i-end j-end]]
  (loop [i (dec i-end)
         j (dec j-end)
         path (transient [])]
    (cond
     (and (= i i-start) (= j j-start)) (reverse (persistent! (conj! path [i-start j-start]))) ; return value
     (and (> i i-start) (= j j-start)) (recur (dec i) j (conj! path [i j]))
     (and (= i i-start) (> j j-start)) (recur i (dec j) (conj! path [i j]))
     (and (> i i-start) (> j j-start))
       (let [diag (mget CA (dec i) (dec j))
             left (mget CA (dec i) j)
             top  (mget CA i (dec j))]
         (cond
          (and (>= left diag) (>= top diag)) (recur (dec i) (dec j) (conj! path [i j]))
          (and (>= diag left) (>= top left)) (recur (dec i) j (conj! path [i j]))
          (and (>= diag top) (>= left top)) (recur i (dec j) (conj! path [i j]))))))))

(defn link-matrix
  "calculate the frechet distance among all possible discrete parametrization
  of the curves P and Q using dist-fn. The discrete frechet distance is
  returned along the coupling sequence."
  ([p2p-dist]
   (link-matrix p2p-dist (apply conj [0 0] (shape p2p-dist))))
  ([p2p-dist [i-start j-start i-end j-end]]
   ;NOTE: the size of the matrix is kept equal to p2p-dist matrix in order
  ; to get the right index for the coupling sequence when the limits passed
  (let [CA         (compute-matrix :vectorz (shape p2p-dist) (fn [i j] -1))
        cd-fn      (fn cd [i j] ;cd : calculate distance
                     (cond
                      (> (mget CA i j) -1) :default ; do nothing
                      (and (= i i-start) (= j j-start)) (mset! CA i j (mget p2p-dist i-start j-start))
                      (and (> i i-start) (= j j-start)) (mset! CA i j (max (cd (dec i) j-start) (mget p2p-dist i j-start)))
                      (and (= i i-start) (> j j-start)) (mset! CA i j (max (cd i-start (dec j)) (mget p2p-dist i-start j)))
                      (and (> i i-start) (> j j-start)) (mset! CA i j (max (min (cd (dec i) j)
                                                                                (cd (dec i) (dec j))
                                                                                (cd i (dec j)))
                                                                           (mget p2p-dist i j))))
                     (mget CA i j))
        ;shape is 1-indexed but mget is zero-indexed
        dist       (cd-fn (dec i-end) (dec j-end))]
    [dist (immutable CA)])))
