(ns openadr3.discovery-test
  "Unit tests for discovery data extraction."
  (:require [clojure.test :refer [deftest is testing]]
            [openadr3.discovery :as disc]))

(deftest vtn-urls-test
  (testing "extracts URLs from discovered services atom"
    (let [d {:services (atom [{:mdns.service/urls ["http://vtn1:8080/openadr3/3.1.0"]}
                              {:mdns.service/urls ["http://vtn2:8080/openadr3/3.1.0"
                                                   "http://vtn2:8443/openadr3/3.1.0"]}])}]
      (is (= ["http://vtn1:8080/openadr3/3.1.0"
              "http://vtn2:8080/openadr3/3.1.0"
              "http://vtn2:8443/openadr3/3.1.0"]
             (disc/vtn-urls d)))))

  (testing "returns empty vec when no services discovered"
    (let [d {:services (atom [])}]
      (is (= [] (disc/vtn-urls d))))))

(deftest default-service-type-test
  (testing "uses OpenADR 3 mDNS service type"
    (is (= "_openadr3._tcp.local." disc/default-service-type))))
