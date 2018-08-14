# -*- coding: utf-8 -*-
"""
Created on Thu Jul 26 10:37:39 2018

@author: WILCBM
"""

import pandas
from sklearn.feature_extraction.text import TfidfVectorizer, CountVectorizer
from sklearn.decomposition import NMF, LatentDirichletAllocation

def print_top_words(model, feature_names, n_top_words):
    for topic_idx, topic in enumerate(model.components_):
        message = "Topic #%d: " % topic_idx
        message += " ".join([feature_names[i] for i in topic.argsort()[:-n_top_words - 1:-1]])
        print(message)
    print()

n_features = 1000

data = pandas.read_csv('event-clustering.csv', sep=',')

data['data'] = data['data'].str.strip()
#data['data'] = data['data'].str.split(' ')

#tfidf_vectorizer = TfidfVectorizer(max_df=0.95, min_df=2,
                                   #max_features=n_features)

#tfidf = tfidf_vectorizer.fit_transform(data['data'])

tf_vectorizer = CountVectorizer(max_df=0.8, min_df=3,
                                max_features=n_features)

tf = tf_vectorizer.fit_transform(data['data'])

lda = LatentDirichletAllocation(n_components=100, max_iter=5,
                                learning_method='online',
                                learning_offset=50.,
                                random_state=0)

lda.fit(tf)

tf_feature_names = tf_vectorizer.get_feature_names()

print_top_words(lda, tf_feature_names, 20)