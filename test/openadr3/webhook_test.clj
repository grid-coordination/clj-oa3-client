(ns openadr3.webhook-test
  "Unit tests for webhook helpers."
  (:require [clojure.test :refer [deftest is testing]]
            [openadr3.webhook :as webhook]))

(deftest callback-url-test
  (testing "constructs URL from receiver map"
    (is (= "http://192.168.1.50:8080/notifications"
           (webhook/callback-url {:callback-host "192.168.1.50"
                                  :port          8080
                                  :path          "/notifications"}))))

  (testing "custom path"
    (is (= "http://10.0.0.1:3000/hooks/openadr"
           (webhook/callback-url {:callback-host "10.0.0.1"
                                  :port          3000
                                  :path          "/hooks/openadr"}))))

  (testing "localhost default"
    (is (= "http://127.0.0.1:0/notifications"
           (webhook/callback-url {:callback-host "127.0.0.1"
                                  :port          0
                                  :path          "/notifications"})))))

(deftest parse-webhook-payload-test
  (let [parse @#'webhook/parse-webhook-payload]
    (testing "plain JSON object"
      (let [result (parse "{\"foo\":\"bar\"}" "/notifications")]
        (is (= {:foo "bar"} result))))

    (testing "double-encoded JSON (VTN-RI behavior)"
      (let [result (parse "\"{\\\"foo\\\":\\\"bar\\\"}\"" "/notifications")]
        (is (= {:foo "bar"} result))))

    (testing "non-JSON string falls through"
      (is (= "not json" (parse "not json" "/notifications"))))))
