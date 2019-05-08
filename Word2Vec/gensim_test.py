# -*- coding: utf-8 -*-
"""
Created on Mon Mar 11 11:57:00 2019

@author: WILCBM
"""

import gensim

model = gensim.models.KeyedVectors.load_word2vec_format('GoogleNews-vectors-negative300.bin', binary=True)

from nltk.cluster import KMeansClusterer
import nltk

class FileToSent(object):    
    def __init__(self, filename):
        self.filename = filename
        self.stop = set(nltk.corpus.stopwords.words('english'))

    def __iter__(self):
        for line in open(self.filename, 'r'):
            ll = [i for i in unicode(line, 'utf-8').lower().split() if i not in self.stop]
            yield ll

X = model[model.vocab]

NUM_CLUSTERS=10
kclusterer = KMeansClusterer(NUM_CLUSTERS, distance=nltk.cluster.util.cosine_distance, repeats=25)
assigned_clusters = kclusterer.cluster(X, assign_clusters=True)
print (assigned_clusters)

from sklearn.manifold import TSNE
from sklearn.manifold import MDS
import matplotlib.pyplot as plt

embedding = MDS(n_components=2)
X_scaled = embedding.fit_transform(X)

def tsne_plot(model):
    "Creates and TSNE model and plots it"
    labels = []
    tokens = []

    for word in model.wv.vocab:
        tokens.append(model[word])
        labels.append(word)
    
    tsne_model = TSNE(perplexity=80, n_components=2, init='pca', n_iter=2500, random_state=23)
    new_values = tsne_model.fit_transform(tokens)

    x = []
    y = []
    for value in new_values:
        x.append(value[0])
        y.append(value[1])
        
    plt.figure(figsize=(16, 16)) 
    for i in range(len(x)):
        plt.scatter(x[i],y[i])
        plt.annotate(labels[i],
                     xy=(x[i], y[i]),
                     xytext=(5, 2),
                     textcoords='offset points',
                     ha='right',
                     va='bottom')
    plt.show()
    
tsne_plot(model)