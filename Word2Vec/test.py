# -*- coding: utf-8 -*-
"""
Created on Mon Mar 11 14:29:54 2019

@author: WILCBM
"""

import string
import gensim
import numpy as np
import pandas as pd
import nltk
from sklearn.cluster import MiniBatchKMeans
from sklearn.metrics import pairwise_distances_argmin_min
from sklearn.manifold import MDS
import matplotlib.pyplot as plt

class FileToDocs(object):    
    def __init__(self, filename):
        self.filename = filename
        self.stop = set(nltk.corpus.stopwords.words('english'))

    def __iter__(self):
        for line in open(self.filename, 'r'):
            ll = [i for i in line.lower().split() if i not in self.stop]
            yield ll
            
model = gensim.models.KeyedVectors.load_word2vec_format('GoogleNews-vectors-negative300.bin', binary=True)  
            
docs = FileToDocs('w2v.train')

wordLists = [doc for doc in docs]

all_words = [word for wordList in wordLists for word in wordList]
unique_words = set()

for word in all_words:
    if word not in unique_words:
        unique_words.add(word)
        
words = list(unique_words)

NumOfWords = len(words)

# construct the n-dimentional array for input data, each row is a word vector
wordVecs = {}
for i in range(0, NumOfWords):
    word = words[i]
    word = word.translate(str.maketrans("", "", string.punctuation))
    if word in model.vocab:
        wordVecs[word] = model[word] 
        
keys = list(wordVecs.keys())
X = [val for val in wordVecs.values()]

K = 50
classifier = MiniBatchKMeans(n_clusters=K, random_state=1, max_iter=100)
classifier.fit(X)

clusters = classifier.predict(X)

df = pd.DataFrame({'word': keys, 'cluster': clusters, 'vector': X})

close = pairwise_distances_argmin_min(classifier.cluster_centers_, X, metric='euclidean')
index_closest_points = close[0]
distance_closest_points = close[1]

for i in range(0, K):
    print("The closest word to the centroid of class {0} is {1}, the distance is {2}".format(i, keys[index_closest_points[i]], distance_closest_points[i]))
    
df_sample = df.sample(1000)

embedding = MDS(n_components=2)
X_scaled = embedding.fit_transform(list(df_sample['vector']))

#plt.scatter(X_scaled[:,0], X_scaled[:,1], c = list(df_sample['cluster']))
#plt.annotate(list(df_sample['word']),
#                     xy=(x[i], y[i]),
#                     xytext=(5, 2),
#                     textcoords='offset points',
#                     ha='right',
#                     va='bottom')

colors = {}
for k in np.arange(K):
    color = '#%02x%02x%02x' % tuple(np.random.choice(range(256), size=3))
    for i in np.arange(len(df_sample)):
        if df_sample.iloc[i, 1] == k:
            colors[i] = color

x = []
y = []
labels = list(df_sample['word'])
for value in X_scaled:
    x.append(value[0])
    y.append(value[1])
    
plt.figure(figsize=(16, 16)) 
for i in range(len(x)):
    color = colors[i]
    plt.scatter(x[i],y[i], c = color)
#    plt.annotate(labels[i],
#                 xy=(x[i], y[i]),
#                 xytext=(5, 2),
#                 textcoords='offset points',
#                 ha='right',
#                 va='bottom')
plt.show()