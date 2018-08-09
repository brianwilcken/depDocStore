# -*- coding: utf-8 -*-
"""
Created on Tue Jul 31 14:01:52 2018

@author: WILCBM
"""

cluster_distance = []
corpus_distance = np.array([])
for i in range(num_clusters):
    #cluster values
    x = cluster_groups.ix[i].values
    #cluster mean (centroid)
    M = cluster_centroids[i]
    #cluster covariance
    V = np.cov(x.T)
    #compute Mahalanobis distance
    mdist = cdist(x, [M], metric='mahalanobis', V=V)[:,0]
    #append those values where the Mahalanobis distance < max_distance
    cluster_distance.append(x[mdist < max_distance])
    #save all distance measurements
    corpus_distance = np.append(corpus_distance, mdist)