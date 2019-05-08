# -*- coding: utf-8 -*-
"""
Created on Thu Apr 25 15:48:22 2019

@author: WILCBM
"""

import pandas as pd
data = pd.read_csv('topic-modeling.data', error_bad_lines=False);
data_text = data[['parsed']]
data_text['index'] = data_text.index
documents = data_text

import gensim
from gensim.utils import simple_preprocess
from gensim.parsing.preprocessing import STOPWORDS
from gensim.models.phrases import Phrases, Phraser
from nltk.stem import PorterStemmer
from nltk.stem.porter import *
import numpy as np
np.random.seed(2018)

stemmer = PorterStemmer()

def lemmatize_stemming(text):
    return stemmer.stem(text)
def preprocess(text):
    result = []
    for token in gensim.utils.simple_preprocess(text):
        if token not in gensim.parsing.preprocessing.STOPWORDS and len(token) > 3:
            result.append(lemmatize_stemming(token))
            #result.append(token)
    return result

#preprocess each document to lemmatize and remove stop words
processed_docs = documents['parsed'].map(preprocess)

docs = [doc for doc in processed_docs]

bigram = Phrases(docs, min_count=5, threshold=10)
trigram = Phrases(bigram[docs], min_count=5, threshold=10)

bigram_model = Phraser(bigram)
trigram_model = Phraser(trigram)

docs = [ bigram_model[doc] for doc in docs ]
docs = [ trigram_model[bigram_model[doc]] for doc in docs ]

dictionary = gensim.corpora.Dictionary(docs)
dictionary.filter_extremes(no_below=3, no_above=0.5, keep_n=100000)

bow_corpus = [dictionary.doc2bow(doc) for doc in docs]
    
from gensim import models
tfidf = models.TfidfModel(bow_corpus)
corpus_tfidf = tfidf[bow_corpus]

from gensim.models.wrappers import LdaMallet

path_to_mallet_binary = "/mallet-2.0.8/bin/mallet"
path_to_mallet_output = "/mallet-2.0.8/output/"
mallet_lda_model = LdaMallet(path_to_mallet_binary, corpus=bow_corpus, num_topics=20, id2word=dictionary, prefix=path_to_mallet_output, workers=8)






#lda_model = gensim.models.LdaModel(bow_corpus, num_topics=20, id2word=dictionary, passes=2)
#
##lda_model = gensim.models.LdaMulticore(bow_corpus, num_topics=10, id2word=dictionary, passes=2, workers=2)
#
#for idx, topic in lda_model.print_topics(-1):
#    print('Topic: {} \nWords: {}'.format(idx, topic))
#    
#lda_model_tfidf = gensim.models.LdaModel(corpus_tfidf, num_topics=20, id2word=dictionary, passes=2)
#    
##lda_model_tfidf = gensim.models.LdaMulticore(corpus_tfidf, num_topics=10, id2word=dictionary, passes=2, workers=4)
#
#for idx, topic in lda_model_tfidf.print_topics(-1):
#    print('Topic: {} Word: {}'.format(idx, topic))
#    
#processed_docs[100]
#
#for index, score in sorted(lda_model[bow_corpus[100]], key=lambda tup: -1*tup[1]):
#    print("\nScore: {}\t \nTopic: {}".format(score, lda_model.print_topic(index, 10)))
#    
#for index, score in sorted(lda_model_tfidf[bow_corpus[100]], key=lambda tup: -1*tup[1]):
#    print("\nScore: {}\t \nTopic: {}".format(score, lda_model_tfidf.print_topic(index, 10)))
#    
#unseen_document = 'Scholars Debate Guiding Students Discussion Links to Online Resources Acknowledgements and Illustration Credits Works Cited and Additional Bibliography Annex Deer Island Diagram I.- Introduction I.A.- How the problem came to be It was not until the 19th Century, like in most America cities, following the path of the hygienization of urban environments, that Boston engaged in the construction of a modern sewage system. Prior to that, both Boston and its neighbouring communities used the geographical advantage of their lay-out. The natural drainage flow towards the beds of the watersheds of the Neponset River to the south, the Charles River to the west, and the Mystic River to the north, and both the southern and northern areas allowed the landowners to channel their waste out of their towns and city by building lanes to the shortest distance, which ultimately resulted in an anarchic grid of combined sewers. The backed-up pressure created by high tides prevented the flow of the sewage to the sea, thus creating constantly a stagnant cesspool-like mass of water that continuously deposited sewage material in the harbor and nearby wetlands and beaches.'
#bow_vector = dictionary.doc2bow(preprocess(unseen_document))
#
#for index, score in sorted(lda_model[bow_vector], key=lambda tup: -1*tup[1]):
#    print("Score: {}\t Topic: {}".format(score, lda_model.print_topic(index, 5)))
#    
#for index, score in sorted(lda_model_tfidf[bow_vector], key=lambda tup: -1*tup[1]):
#    print("Score: {}\t Topic: {}".format(score, lda_model_tfidf.print_topic(index, 5)))
#    
#for index, score in sorted(mallet_lda_model[bow_vector], key=lambda tup: -1*tup[1]):
#    print("Score: {}\t Topic: {}".format(score, mallet_lda_model.print_topic(index, 5)))