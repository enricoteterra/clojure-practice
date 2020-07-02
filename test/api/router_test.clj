(ns api.router-test
  (:require [api.core :refer :all]
            [clojure.test :refer :all]
            [muuntaja.core :as m]))

(defn parse-body [request] (m/decode "application/json" (:body request)))
(deftest http-routes-test
  (testing "it should respond to GET `/tasks`"
    (let [response ((app (in-memory-event-store))
                    {:request-method :get :uri "/tasks"})]
      (is (= 200 (:status response)))
      (is (= [] (parse-body response))))

    (let [bad-event-store (in-memory-event-store)]
      ((:send bad-event-store) {:event/name "bad event"})
      (is (= 500 (:status ((app bad-event-store)
                           {:request-method :get :uri "/tasks"}))))))

  (testing "it should respond to POST `/tasks/added`"
    (is (= 400 (-> {:request-method :post :uri "/tasks/added"}
                   ((app (in-memory-event-store)))
                   (:status))))

    (is (= 201 (-> {:request-method :post
                    :uri            "/tasks/added"
                    :body-params    {:uri "uri-1" :title "new task"}}
                   ((app (in-memory-event-store)))
                   (:status)))))

  (testing "it should respond to POST `/tasks/completed`"
    (is (= 400 (-> {:request-method :post, :uri "/tasks/completed"}
                   ((app (in-memory-event-store)))
                   (:status))))

    (is (= 201 (-> {:request-method :post
                    :uri            "/tasks/completed"
                    :body-params    {:uri "uri-1" :title "new task"}}
                   ((app (in-memory-event-store)))
                   (:status)))))

  (testing "it should return 404 when request uri unknown"
    (is (= 404 (-> {:request-method :get :uri "some-uri"}
                   ((app (in-memory-event-store)))
                   (:status)))))

  (testing "it should return 405 when request method is not allowed"
    (is (= 405 (-> {:request-method :post :uri "/tasks"}
                   ((app (in-memory-event-store)))
                   (:status))))))