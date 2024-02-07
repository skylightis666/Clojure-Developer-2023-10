(ns otus-16.homework
    (:require [clojure.set :as set]))

(def start-number-state {:total-bytes   0
                         :bytes-per-url 0})

(def start-set-state {:urls-per-referrer #{}})

(def number-state (atom start-number-state))

(def set-state (atom start-set-state))

(defn reset-state []
    (reset! number-state start-number-state)
    (reset! set-state start-set-state))

(defn parse-apache-log [log-line]
    (let [regex #"(\S+) (\S+) (\S+) \[([\w:/]+\s[\+\-]\d{4})\] \"(.*?)\" (\d{3}) (\S+) \"(.*?)\" \"(.*?)\""
          matcher (re-find regex log-line)]
        (if matcher
            (let [request (nth matcher 5)
                  [method url http-version] (clojure.string/split request #" ")]
                {:ip         (nth matcher 1)
                 :identifier (nth matcher 2)
                 :userid     (nth matcher 3)
                 :time       (nth matcher 4)
                 :request    {:method       method
                              :url          url
                              :http-version http-version}
                 :status     (nth matcher 6)
                 :size       (parse-long (nth matcher 7))
                 :referrer   (nth matcher 8)
                 :user-agent (nth matcher 9)})
            :no-match)))


(defn size-reducer [lines]
    (reduce #(+ %1 (:size %2)) 0 lines))

(defn has-url? [url]
    (fn [el]
        (= (:url (:request el)) url)))

(defn has-referrer? [referrer]
    (fn [el]
        (= (:referrer el) referrer)))

(defn parse&persist-logs-portion [url referrer log-lines]
    (let [parsed-lines (map parse-apache-log log-lines)
          modified-states (reduce
                              (fn [{:keys [total-numbers set-states]} parsed-line]
                                  (let [bytes (:size parsed-line)
                                        line-url (:url (:request parsed-line))
                                        line-referrer (:referrer parsed-line)
                                        total-numbers (update total-numbers :total-bytes + bytes)
                                        total-numbers (if (or (= url line-url) (= url :all))
                                                          (update total-numbers :bytes-per-url + bytes)
                                                          total-numbers)
                                        set-states (if (or (= referrer line-referrer) (= referrer :all))
                                                       (update set-states :urls-per-referrer conj line-url)
                                                       set-states)]
                                      {:total-numbers total-numbers, :set-states set-states}))
                              {:total-numbers start-number-state, :set-states start-set-state}
                              parsed-lines)]
        (swap! number-state #(merge-with + % (:total-numbers modified-states)))
        (swap! set-state #(merge-with set/union % (:set-states modified-states)))))

(defn parallel-parse-file [url referrer file]
    (with-open [rdr (clojure.java.io/reader file)]
        (doall (pmap (partial parse&persist-logs-portion url referrer) (partition-all 100000 (line-seq rdr))))))

(defn solution [& {:keys [url referrer]
                   :or   {url :all referrer :all}}]
    (let [files (->> "logs"
                     clojure.java.io/file
                     file-seq
                     (filter #(.isFile %)))]
        (doall (pmap (partial parallel-parse-file url referrer) files)))
    (let [result1 @number-state
          result2 @set-state]                               ; TODO closure with atom?
        (reset-state)
        (merge result1 {:urls-per-referrer (count (:urls-per-referrer result2))})))


(comment
    ;; возможные вызовы функции
    (time (solution))
    @number-state
    @set-state
    (reset-state)
    (time (solution :url "/random"))
    (time (solution :referrer "https://www.google.com/"))
    (time (solution :url "/%D1%81%D1%82%D0%BE%D1%8F%D1%82-%D0%BF%D0%B0%D1%81%D1%81%D0%B0%D0%B6%D0%B8%D1%80%D1%8B-%D0%B2-%D0%B0%D1%8D%D1%80%D0%BE%D0%BF%D0%BE%D1%80%D1%82%D1%83-%D0%BF%D0%BE%D1%81%D0%B0%D0%B4%D0%BE%D1%87%D0%BD%D1%8B%D0%B9-%D0%B4%D0%BE%D1%81%D0%BC%D0%BE%D1%82%D1%80/?p=4"
                    :referrer "https://www.google.com/")))
