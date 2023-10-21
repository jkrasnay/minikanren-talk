(ns minikanren-talk
  (:require [nextjournal.clerk :as clerk]
            [nextjournal.clerk-slideshow :as slideshow]))


(defn start
  [_opts]
  (clerk/add-viewers! [slideshow/viewer])
  (clerk/serve! {:browse true :watch-paths ["notebooks"]}))
