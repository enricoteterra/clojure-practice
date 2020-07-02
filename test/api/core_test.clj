(ns api.core-test
  (:require [api.core :refer :all]
            [clojure.test :refer :all]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :refer [generate]]))

(defn some-task-event [& [overwrites]]
  (into (generate (s/gen :app/task-event)) overwrites))

(deftest apply-event-test
  (testing "applying unknown event returns an error"
    (let [event {:event/name "some event"}]
      (is (thrown? clojure.lang.ExceptionInfo (apply-event [] event)))))

  (testing "`task-added` event is applied"
    (is (seq (apply-event [] (some-task-event {:event/name "task-added"})))))

  (testing "many `task-added` events with same uri are applied"
    (let [tasks (->> [(some-task-event
                        {:event/name "task-added"
                         :task/title "first event" :task/uri "uri-1"})
                      (some-task-event
                        {:event/name "task-added"
                         :task/title "second event" :task/uri "uri-1"})
                      (some-task-event
                        {:event/name "task-added"
                         :task/title "last event" :task/uri "uri-1"})]
                     (reduce apply-event []))]
      (is (= 1 (count tasks)))
      (is (= "last event" (:task/title (first tasks))))))

  (testing "`task-completed` event is applied"
    (is (empty? (->> (some-task-event {:event/name "task-completed"})
                     (apply-event []))))

    (is (empty? (->> [(some-task-event {:event/name "task-added"
                                        :task/uri   "1"})
                      (some-task-event {:event/name "task-completed"
                                        :task/uri   "1"})]
                     (reduce apply-event []))))

    (is (= 1 (->> [(some-task-event {:event/name "task-added"
                                     :task/uri   "1"})
                   (some-task-event {:event/name "task-completed"
                                     :task/uri   "2"})]
                  (reduce apply-event [])
                  (count))))

    (is (= 0 (->> [(some-task-event {:event/name "task-added"
                                     :task/uri   "1"})
                   (some-task-event {:event/name "task-completed"
                                     :task/uri   "1"})]
                  (reduce apply-event [])
                  (count))))))

(deftest in-memory-event-store-test
  (testing "it should keep a record of events"
    (let [{:keys [send, events]} (in-memory-event-store)]
      (is (empty? (events)))
      (send {:event/name "first event"})
      (is (= 1 (count (events))))
      (send {:event/name "another event"})
      (is (events) [{:event/name "first event"}
                    {:event/name "another event"}])))

  (testing "it should not store invalid events"
    (let [{:keys [send, events]} (in-memory-event-store)]
      (send {:some-prop "first event"})
      (is (= 0 (count (events))))))

  (testing "it should not share state with other stores"
    (is (empty? ((:events (in-memory-event-store)))))))