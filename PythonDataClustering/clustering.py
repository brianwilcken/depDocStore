from __future__ import print_function

import numpy as np
import pandas as pd
import nltk
import re
import itertools
import TopToolbar
#from sklearn import feature_extraction
from nltk.stem.snowball import SnowballStemmer
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.cluster import KMeans
#from sklearn.decomposition import TruncatedSVD
#from sklearn.decomposition import PCA
from scipy.spatial.distance import cdist
import matplotlib.pyplot as plt
#import matplotlib as mpl
from sklearn.manifold import MDS
import mpld3

num_clusters = 7

data = pd.read_csv('event-clustering.csv', sep=',', encoding='utf-8')

stopwords = nltk.corpus.stopwords.words('english')

stemmer = SnowballStemmer("english")

def tokenize_and_stem(text):
    # first tokenize by sentence, then by word to ensure that punctuation is caught as it's own token
    tokens = [word for sent in nltk.sent_tokenize(text) for word in nltk.word_tokenize(sent)]
    filtered_tokens = []
    # filter out any tokens not containing letters (e.g., numeric tokens, raw punctuation)
    for token in tokens:
        if re.search('[a-zA-Z]', token):
            filtered_tokens.append(token)
    stems = [stemmer.stem(t) for t in filtered_tokens]
    return stems

def tokenize_only(text):
    # first tokenize by sentence, then by word to ensure that punctuation is caught as it's own token
    tokens = [word.lower() for sent in nltk.sent_tokenize(text) for word in nltk.word_tokenize(sent)]
    filtered_tokens = []
    # filter out any tokens not containing letters (e.g., numeric tokens, raw punctuation)
    for token in tokens:
        if re.search('[a-zA-Z]', token):
            filtered_tokens.append(token)
    return filtered_tokens

#stem and tokenize as preprocessing for tf-idf vectorize step
totalvocab_stemmed = []
totalvocab_tokenized = []
for summary in data['summary']:
    allwords_stemmed = tokenize_and_stem(summary) #for each item in 'synopses', tokenize/stem
    totalvocab_stemmed.extend(allwords_stemmed) #extend the 'totalvocab_stemmed' list
    
    allwords_tokenized = tokenize_only(summary)
    totalvocab_tokenized.extend(allwords_tokenized)
    

vocab_frame = pd.DataFrame({'words': totalvocab_tokenized}, index = totalvocab_stemmed)

tfidf_vectorizer = TfidfVectorizer(max_df=0.6, max_features=200000,
                                 min_df=0.01, stop_words='english',
                                 use_idf=True, tokenizer=tokenize_and_stem, ngram_range=(1,3))

tfidf_matrix = tfidf_vectorizer.fit_transform(data['summary'])

terms = tfidf_vectorizer.get_feature_names()

similarity = cosine_similarity(tfidf_matrix)

dist = 1 - similarity

#process the TF-IDF matrix using K-means clustering to form cluster vector
km = KMeans(n_clusters=num_clusters)
km.fit(tfidf_matrix)

#extract the cluster vector
clusters = km.labels_.tolist()

#docs = { 'id': list(data['id']), 'title': list(data['title']), 'summary': list(data['summary']), 'cluster': clusters }
#frame = pd.DataFrame(docs, index = [clusters] , columns = ['id', 'title', 'cluster'])
#frame['cluster'].value_counts()


order_centroids = km.cluster_centers_.argsort()[:, ::-1]

#
#
#for i in range(num_clusters):
#    print("Cluster %d words:" % i)
#    
#    for ind in order_centroids[i, :6]: #replace 6 with n words per cluster
#        print(' %s' % vocab_frame.ix[terms[ind].split(' ')].values.tolist()[0][0].encode('utf-8', 'ignore'))
#    print #add whitespace
#    print #add whitespace
    
#    print("Cluster %d titles:" % i)
#    for title in frame.ix[i]['title'].values.tolist():
#        print(' %s,' % title)
#    print #add whitespace
#    print #add whitespace

#svd = TruncatedSVD(n_components=2)
#pos = svd.fit_transform(tfidf_matrix)

##perform dimensionality reduction using PCA technique to reduce the dimensions of the cosine distance matrix to n_docs x 2
#pca = PCA(n_components=2)
#pos = pca.fit_transform(dist)

#perform dimensionality reduction using MDS technique to reduce the dimensions of the cosine distance matrix to n_docs x 2
mds = MDS(n_components=2, dissimilarity="precomputed", random_state=1)
pos = mds.fit_transform(dist)

#get x,y coordinates of each document in the MDS-reduced position matrix
xs, ys = pos[:, 0], pos[:, 1]

#find cluster centroid of each document cluster
cluster_groups = pd.DataFrame(pos, index = [clusters], columns = ['x-pos', 'y-pos'])
cluster_centroids = []
for i in range(num_clusters):
    cluster_x = np.mean(cluster_groups.ix[i]['x-pos'])
    cluster_y = np.mean(cluster_groups.ix[i]['y-pos'])
    cluster_centroids.append([cluster_x, cluster_y])
cluster_centroids = np.array(cluster_centroids)

#compute the Mahalanobis distance of each point in each cluster
max_distance = 1
cluster_distance = []
cluster_groups['mdist'] = np.nan
for i in range(num_clusters):
    #cluster values
    x = cluster_groups.loc[i, ('x-pos', 'y-pos')].values
    #cluster mean (centroid)
    M = cluster_centroids[i]
    #cluster covariance
    V = np.cov(x.T)
    #compute Mahalanobis distance
    mdist = cdist(x, [M], metric='mahalanobis', V=V)[:,0]
    #append those values where the Mahalanobis distance < max_distance
    cluster_distance.append(x[mdist < max_distance])
    #save all distance measurements
    cluster_groups.loc[i, 'mdist'] = mdist


#plot formatting stuff...
cluster_colors = itertools.cycle(('b', 'g', 'r', 'y', 'k', 'm', 'c', '#0f6649', '#db7c2e', '#88a4d8'))

cluster_names = {}

for i in range(num_clusters):
    topTermIndices = order_centroids[i, :10]
    topTerms = []
    for ind in order_centroids[i, :10]:
        topTerms.append(vocab_frame.ix[terms[ind].split(' ')].values.tolist()[0][0])
    cluster_names[i] = ', '.join(topTerms)

cluster_markers = itertools.cycle(('o','X','D','^','s','*','<','v','p'))

df = pd.DataFrame(dict(x=xs, y=ys, label=clusters, title=list(data['title']))) 

groups = df.groupby('label')

#form data frame summarizing document clusterization with distance from mean for each document
doc_cluster_Df = pd.DataFrame(dict(id=list(data['id']), title=list(data['title']), cluster=[cluster_names[i] for i in clusters], distance=list(cluster_groups.loc[:]['mdist'])))
doc_cluster_Df.to_csv('doc_clusters.csv', sep=',')

#define custom css to format the font and to remove the axis labeling
css = """
text.mpld3-text, div.mpld3-tooltip {
  font-family:Arial, Helvetica, sans-serif;
}

g.mpld3-xaxis, g.mpld3-yaxis {
display: none; }

svg.mpld3-figure {
margin-left: -200px;}
"""

#plot the clusters
fig, ax = plt.subplots(figsize=(17, 9)) # set size
ax.margins(0.05) # Optional, just adds 5% padding to the autoscaling

#iterate through groups to layer the plot
#note that I use the cluster_name and cluster_color dicts with the 'name' lookup to return the appropriate color/label
for name, group in groups:
    curr_marker = next(cluster_markers)
    curr_color = next(cluster_colors)
    #mark each point where the Mahalanobis distance < max_distance
    ax.plot(cluster_distance[name][:, 0], cluster_distance[name][:, 1], linestyle='',
            marker='o', ms=10, color=curr_color, alpha=0.3)
    #plot the cluster
    points = ax.plot(group.x, group.y, marker=curr_marker, linestyle='', ms=6, 
            label=cluster_names[name], color=curr_color, mec='none')
    #plot the cluster centroids
    ax.plot(cluster_centroids[name, 0], cluster_centroids[name, 1], 
            marker=curr_marker, color=curr_color, ms=12)
    ax.set_aspect('auto')
    labels = [i for i in group.title]
    #set tooltip using points, labels and the already defined 'css'
    tooltip = mpld3.plugins.PointHTMLTooltip(points[0], labels,
                                       voffset=10, hoffset=10, css=css)
    
    #connect tooltip to fig
    mpld3.plugins.connect(fig, tooltip, TopToolbar.TopToolbar())   
    
    #set tick marks as blank
    ax.axes.get_xaxis().set_ticks([])
    ax.axes.get_yaxis().set_ticks([])
    
    #set axis as blank
    ax.axes.get_xaxis().set_visible(False)
    ax.axes.get_yaxis().set_visible(False)
    
#    ax.tick_params(\
#        axis= 'x',          # changes apply to the x-axis
#        which='both',      # both major and minor ticks are affected
#        bottom='on',      # ticks along the bottom edge are off
#        top='off',         # ticks along the top edge are off
#        labelbottom='off')
#    ax.tick_params(\
#        axis= 'y',         # changes apply to the y-axis
#        which='both',      # both major and minor ticks are affected
#        left='on',      # ticks along the bottom edge are off
#        top='off',         # ticks along the top edge are off
#        labelleft='off')
    
ax.legend(numpoints=1)

#for i in range(len(df)):
#    ax.text(df.iloc[i]['x'], df.iloc[i]['y'], df.iloc[i]['title'][0:40], size=8)  
    
#plt.show()

#mpld3.display() #show the plot

html = mpld3.fig_to_html(fig)
np.savetxt('analysis.html', [html], fmt='%s')