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
            ["be" "nimble" "Jack"]
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
    (is (= 1 (count (sentences "P.T. Barnum owns a circus."))))))
