(ns openadr3.client.ven-test
  "Unit tests for VEN client URI normalization."
  (:require [clojure.test :refer [deftest is testing]]
            [openadr3.client.ven :as ven]))

(def ^:private normalize-notifier-uri
  "Access the private normalize-notifier-uri fn via var."
  @#'ven/normalize-notifier-uri)

(deftest normalize-notifier-uri-test
  (testing "tcp:// normalizes to mqtt:// with default port"
    (is (= "mqtt://broker.example.com:1883"
           (normalize-notifier-uri "tcp://broker.example.com"))))

  (testing "tcp:// preserves explicit port"
    (is (= "mqtt://broker.example.com:9883"
           (normalize-notifier-uri "tcp://broker.example.com:9883"))))

  (testing "ssl:// normalizes to mqtts:// with default port"
    (is (= "mqtts://broker.example.com:8883"
           (normalize-notifier-uri "ssl://broker.example.com"))))

  (testing "ssl:// preserves explicit port"
    (is (= "mqtts://broker.example.com:9883"
           (normalize-notifier-uri "ssl://broker.example.com:9883"))))

  (testing "mqtt:// passes through with default port"
    (is (= "mqtt://broker.example.com:1883"
           (normalize-notifier-uri "mqtt://broker.example.com"))))

  (testing "mqtt:// preserves explicit port"
    (is (= "mqtt://broker.example.com:1883"
           (normalize-notifier-uri "mqtt://broker.example.com:1883"))))

  (testing "mqtts:// passes through with default port"
    (is (= "mqtts://broker.example.com:8883"
           (normalize-notifier-uri "mqtts://broker.example.com"))))

  (testing "mqtts:// preserves explicit port"
    (is (= "mqtts://broker.example.com:8883"
           (normalize-notifier-uri "mqtts://broker.example.com:8883"))))

  (testing "unknown scheme passes through unchanged"
    (is (= "wss://broker.example.com:443/mqtt"
           (normalize-notifier-uri "wss://broker.example.com:443/mqtt")))))
