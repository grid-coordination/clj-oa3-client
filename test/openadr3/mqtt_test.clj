(ns openadr3.mqtt-test
  "Unit tests for MQTT URI normalization."
  (:require [clojure.test :refer [deftest is testing]]
            [openadr3.mqtt :as mqtt]))

(def ^:private normalize-broker-uri
  "Access the private normalize-broker-uri fn via var."
  @#'mqtt/normalize-broker-uri)

(deftest normalize-broker-uri-test
  (testing "mqtt:// translates to tcp:// with default port"
    (is (= "tcp://broker.example.com:1883"
           (normalize-broker-uri "mqtt://broker.example.com"))))

  (testing "mqtt:// preserves explicit port"
    (is (= "tcp://broker.example.com:9883"
           (normalize-broker-uri "mqtt://broker.example.com:9883"))))

  (testing "mqtts:// translates to ssl:// with default port"
    (is (= "ssl://broker.example.com:8883"
           (normalize-broker-uri "mqtts://broker.example.com"))))

  (testing "mqtts:// preserves explicit port"
    (is (= "ssl://broker.example.com:9883"
           (normalize-broker-uri "mqtts://broker.example.com:9883"))))

  (testing "tcp:// passes through with default port"
    (is (= "tcp://broker.example.com:1883"
           (normalize-broker-uri "tcp://broker.example.com"))))

  (testing "tcp:// preserves explicit port"
    (is (= "tcp://broker.example.com:1883"
           (normalize-broker-uri "tcp://broker.example.com:1883"))))

  (testing "ssl:// passes through with default port"
    (is (= "ssl://broker.example.com:8883"
           (normalize-broker-uri "ssl://broker.example.com"))))

  (testing "ssl:// preserves explicit port"
    (is (= "ssl://broker.example.com:8883"
           (normalize-broker-uri "ssl://broker.example.com:8883")))))
