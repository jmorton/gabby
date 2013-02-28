(ns gabby.util-test
  (:use clojure.test
        gabby.util))

(deftest util
  (testing "Trigrams"
    (is (= (trigrams "")
           [["*" "*" "~"]]))
    (is (= (trigrams "foo")
           [["*" "*" "foo"]
            [ "*" "foo" "~"]]))
    (is (= (trigrams "Foo Bar")
           [["*" "*" "foo"]
            ["*" "foo" "bar"]
            ["foo" "bar" "~"]]))
    (is (= (trigrams "Jack be nimble.")
           [["*" "*" "jack"]
            ["*" "jack" "be"]
            ["jack" "be" "nimble"]
            ["be" "nimble" "~"]]))
    (is (= (trigrams "Jack be nimble, Jack be quick.")
           [["*" "*" "jack"]
            ["*" "jack" "be"]
            ["jack" "be" "nimble"]
            ["be" "nimble" "jack"]
            ["nimble" "jack" "be"]
            ["jack" "be" "quick"]
            ["be" "quick" "~"]])))
  (testing "Sentences"
    (is (= 3 (count (sentences
                     "It was a pleasure to burn. It was a special pleasure
                      to see things eaten, to see things blackened and changed.
                      With the brass nozzle in his fists, with this giant
                      python spitting its venomous kerosene upon the world,
                      the blood pounded in his head, and his hands were the
                      hands of some amazing conductor playing all the symphonies
                      of blazing and burning to bring down the tatters and
                      charcoal ruins of history."))))
    (is (= 3 (count (sentences "P.T. Barnum owns a circus.")))))
  (testing "Word count"
    (is (= 1 (word-count "One")))
    (is (= 2 (word-count "Hello World!")))
    ; Not quite right, but if it changes, that could throw
    ; other things off.
    (is (= 6 (word-count "P.T. Barnum owns a circus."))))
  (testing "Perplexity"
    (is
     (let [params {["*" "*" "the"]        1
                   ["*" "the" "dog"]      0.5
                   ["*" "the" "cat"]      0.5
                   ["the" "cat" "walks"]  1
                   ["cat" "walks" "~"]    1
                   ["the" "dog" "runs"]   1
                   ["dog" "runs" "~"]     1
                   }
           test ["the dog runs"
                 "the cat walks"
                 "the dog runs"]]
       (is (> 1.19 (perplexity test params) 1.189))))))
