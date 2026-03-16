(ns openadr3.client.base-test
  "Unit tests for client base helpers."
  (:require [clojure.test :refer [deftest is testing]]
            [openadr3.client.base :as base]))

(deftest extract-topics-test
  (testing "extracts topic values from successful response"
    (is (= ["programs/create" "events/update"]
           (vec (base/extract-topics {:status 200
                                      :body {:topics {:a "programs/create"
                                                      :b "events/update"}}})))))

  (testing "returns nil for error status"
    (is (nil? (base/extract-topics {:status 404
                                    :body {:error "not found"}}))))

  (testing "returns nil for 300+ status"
    (is (nil? (base/extract-topics {:status 301
                                    :body {:topics {:a "x"}}}))))

  (testing "returns empty seq for response with no topics"
    (is (empty? (base/extract-topics {:status 200
                                      :body {:topics {}}})))))

(deftest martian-test
  (testing "returns :martian value from client map"
    (is (= :mock-martian (base/martian {:martian :mock-martian}))))

  (testing "throws when :martian is nil"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Client not started"
                          (base/martian {:client-type :ven})))))
