(ns openadr3.net-test
  "Unit tests for network utilities."
  (:require [clojure.test :refer [deftest is testing]]
            [openadr3.net :as net]))

(deftest detect-lan-ip-test
  (testing "returns a non-empty IP string"
    (let [ip (net/detect-lan-ip)]
      (is (string? ip))
      (is (pos? (count ip))))))

(deftest local-ip-addresses-test
  (testing "returns a vector of strings"
    (let [addrs (net/local-ip-addresses)]
      (is (vector? addrs))
      (doseq [addr addrs]
        (is (string? addr))
        (is (re-matches #"\d+\.\d+\.\d+\.\d+" addr))))))
