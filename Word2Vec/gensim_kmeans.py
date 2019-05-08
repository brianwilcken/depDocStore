# -*- coding: utf-8 -*-
"""
Created on Mon Mar 11 14:14:38 2019

@author: WILCBM
"""

import gensim
import numpy as np
import pandas as pd
import nltk
from sklearn.cluster import MiniBatchKMeans
from sklearn.metrics import pairwise_distances_argmin_min

model = gensim.models.KeyedVectors.load_word2vec_format('GoogleNews-vectors-negative300.bin', binary=True)  

K=3

words = ["ship", "car", "truck", "bus", "vehicle", "bike", "tractor", "boat",
       "apple", "banana", "fruit", "pear", "orange", "pineapple", "watermelon",
       "dog", "pig", "animal", "cat", "monkey", "snake", "tiger", "rat", "duck", "rabbit", "fox"]
NumOfWords = len(words)

# construct the n-dimentional array for input data, each row is a word vector
x = np.zeros((NumOfWords, model.vector_size))
for i in range(0, NumOfWords):
    x[i,]=model[words[i]] 

# train the k-means model
classifier = MiniBatchKMeans(n_clusters=K, random_state=1, max_iter=100)
classifier.fit(x)

# check whether the words are clustered correctly
print(classifier.predict(x))

# find the index and the distance of the closest points from x to each class centroid
close = pairwise_distances_argmin_min(classifier.cluster_centers_, x, metric='euclidean')
index_closest_points = close[0]
distance_closest_points = close[1]

for i in range(0, K):
    print("The closest word to the centroid of class {0} is {1}, the distance is {2}".format(i, words[index_closest_points[i]], distance_closest_points[i]))
    