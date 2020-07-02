(ns api.features-test
  (:require [api.core :refer :all]
            [clojure.test :refer :all]
            [muuntaja.core :as m]))

(defn parse-body [request] (m/decode "application/json" (:body request)))
(deftest features-test
  (testing "it should add tasks"
    (let [app (app (in-memory-event-store))]
      (app {:request-method :post
            :uri            "/tasks/added"
            :body-params    {:uri "uri-1" :title "new task"}})
      (app {:request-method :post
            :uri            "/tasks/added"
            :body-params    {:uri "uri-2" :title "second task"}})
      (is (= 2 (count (parse-body (app {:request-method :get :uri "/tasks"})))))))

  (testing "it should complete tasks"
    (let [app (app (in-memory-event-store))]
      (app {:request-method :post
            :uri            "/tasks/added"
            :body-params    {:uri "uri-1" :title "new task"}})
      (is (= 1 (count (parse-body (app {:request-method :get :uri "/tasks"})))))
      (app {:request-method :post
            :uri            "/tasks/completed"
            :body-params    {:uri "uri-1" :title "new task"}})
      (is (= 0 (count (parse-body (app {:request-method :get :uri "/tasks"}))))))))