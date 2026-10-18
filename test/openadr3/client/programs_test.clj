(ns openadr3.client.programs-test
  "Paged program lookups and query-param extensions, against a stub VTN."
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [com.stuartsierra.component :as component]
            [openadr3.client.base :as base]
            [openadr3.client.bl :as bl])
  (:import [com.sun.net.httpserver HttpExchange HttpHandler HttpServer]
           [java.net InetSocketAddress]))

(defn- query-map [^String q]
  (into {} (for [kv (str/split (or q "") #"&") :when (seq kv)
                 :let [[k v] (str/split kv #"=" 2)]]
             [k (java.net.URLDecoder/decode (str v) "UTF-8")])))

(defn- program [i]
  {:id (str "p" i) :programName (str "prog-" i)
   :createdDateTime "2026-01-01T00:00:00Z" :modificationDateTime "2026-01-01T00:00:00Z"
   :objectType "PROGRAM"})

(defn- with-stub-vtn
  "Serve n programs with skip/limit paging; call f with the base URL and an
  atom of the query maps received."
  [n status f]
  (let [queries (atom [])
        server  (HttpServer/create (InetSocketAddress. "127.0.0.1" 0) 0)]
    (.createContext server "/"
                    (reify HttpHandler
                      (handle [_ ex]
                        (let [^HttpExchange ex ex
                              q     (query-map (.getRawQuery (.getRequestURI ex)))
                              skip  (parse-long (get q "skip" "0"))
                              limit (parse-long (get q "limit" "50"))
                              body  (.getBytes ^String (json/write-str
                                                        (->> (range n) (drop skip) (take limit) (mapv program))))]
                          (swap! queries conj (assoc q :path (.getPath (.getRequestURI ex))))
                          (.add (.getResponseHeaders ex) "Content-Type" "application/json")
                          (.sendResponseHeaders ex status (alength body))
                          (with-open [os (.getResponseBody ex)] (.write os body))))))
    (.start server)
    (try
      (f (str "http://127.0.0.1:" (.getPort (.getAddress server)) "/openadr3/3.1.0") queries)
      (finally (.stop server 0)))))

(defn- start [url opts]
  (component/start (bl/bl-client (merge {:url url :token "t"} opts))))

(deftest paged-programs-test
  (with-stub-vtn 120 200
    (fn [url queries]
      (let [c (start url {})]
        (testing "programs fetches every page"
          (is (= 120 (count (base/programs c))))
          (is (= ["0" "50" "100"] (mapv #(get % "skip") @queries))))
        (testing "find-program-by-name finds a match past the first page"
          (is (= "p110" (:id (base/find-program-by-name c "prog-110"))))
          (is (nil? (base/find-program-by-name c "nope"))))
        (testing "programName is dropped without the extension"
          (is (not-any? #(contains? % "programName") @queries)))
        (component/stop c)))))

(deftest query-param-extensions-test
  (with-stub-vtn 3 200
    (fn [url queries]
      (let [c (start url {:query-param-extensions
                          {:search-all-programs [:programName]
                           :search-all-events   [:dateStart :dateEnd]}})]
        (testing "find-program-by-name sends programName when enabled"
          (base/find-program-by-name c "prog-1")
          (is (= "prog-1" (get (last @queries) "programName"))))
        (testing "search-events passes extension params through"
          (base/search-events c {:programID "p" :dateStart "2026-01-01T00:00:00Z"
                                 :dateEnd "2026-01-02T00:00:00Z"})
          (is (= {"programID" "p" "dateStart" "2026-01-01T00:00:00Z"
                  "dateEnd" "2026-01-02T00:00:00Z"}
                 (select-keys (last @queries) ["programID" "dateStart" "dateEnd"]))))
        (component/stop c)))))

(deftest program-search-error-test
  (with-stub-vtn 3 500
    (fn [url _]
      (let [c (start url {})]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Program search failed"
                              (base/programs c)))
        (component/stop c)))))
