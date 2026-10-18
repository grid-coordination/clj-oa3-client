(ns openadr3.client.bl-test
  "Unit tests for BlClient timeouts."
  (:require [clojure.test :refer [deftest is testing]]
            [com.stuartsierra.component :as component]
            [openadr3.client.base :as base]
            [openadr3.client.bl :as bl])
  (:import [java.net ServerSocket]
           [java.net.http HttpTimeoutException]))

(defn- with-silent-server
  "Call f with the URL of a server that accepts connections and never answers."
  [f]
  (with-open [ss (ServerSocket. 0)]
    (f (str "http://127.0.0.1:" (.getLocalPort ss) "/openadr3/3.1.0"))))

(deftest request-timeout-test
  (testing "a request exceeding :request-timeout-ms throws, caused by HttpTimeoutException"
    (with-silent-server
      (fn [url]
        (let [c (component/start (bl/bl-client {:url url :token "t"
                                                :request-timeout-ms 500}))]
          (try
            (let [e (try (base/get-programs c) nil
                         (catch clojure.lang.ExceptionInfo e e))]
              (is (instance? HttpTimeoutException (ex-cause e)))
              (is (= HttpTimeoutException (:type (ex-data e)))))
            (finally (component/stop c))))))))

(deftest timeout-options-test
  (testing "connect timeout reaches the HttpClient"
    (let [c (component/start (bl/bl-client {:url "http://127.0.0.1:1/x" :token "t"
                                            :connect-timeout-ms 1234}))]
      (is (= 1234 (-> ^java.net.http.HttpClient (:http-client c)
                      .connectTimeout .get .toMillis)))
      (component/stop c)))

  (testing "defaults: 5000 ms connect timeout, no request-timeout interceptor"
    (let [c (component/start (bl/bl-client {:url "http://127.0.0.1:1/x" :token "t"}))]
      (is (= 5000 (-> ^java.net.http.HttpClient (:http-client c)
                      .connectTimeout .get .toMillis)))
      (is (not-any? #(= ::base/request-timeout (:name %))
                    (:interceptors (:martian c))))
      (component/stop c))))
