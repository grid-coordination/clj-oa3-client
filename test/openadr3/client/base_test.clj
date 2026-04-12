(ns openadr3.client.base-test
  "Unit tests for client base helpers."
  (:require [clojure.test :refer [deftest is testing]]
            [openadr3.client.base :as base]))

(deftest compose-user-agent-test
  (testing "with no caller user-agent, returns just clj-oa3-client identity"
    (is (= (str "clj-oa3-client/" base/lib-version)
           (base/compose-user-agent nil))))

  (testing "with caller user-agent, prepends clj-oa3-client identity"
    (is (= (str "clj-oa3-client/" base/lib-version " my-app/1.0")
           (base/compose-user-agent "my-app/1.0"))))

  (testing "with caller user-agent including contact info"
    (is (= (str "clj-oa3-client/" base/lib-version " my-app/2.0 (contact@example.com)")
           (base/compose-user-agent "my-app/2.0 (contact@example.com)")))))

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
