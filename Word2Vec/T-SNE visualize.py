# -*- coding: utf-8 -*-
"""
Created on Wed Mar  6 15:00:29 2019

@author: WILCBM
"""

import csv

filename = 'tsne-standard-coords.csv'

# initializing the titles and rows list  
rows = [] 

# reading csv file 
with open(filename, 'r') as csvfile: 
    # creating a csv reader object 
    csvreader = csv.reader(csvfile) 
  
    # extracting each data row one by one 
    for row in csvreader: 
        rows.append(row) 
  
    # get total number of rows 
    print("Total no. of rows: %d"%(csvreader.line_num))
    
import random

sample = random.sample(rows, 5000)
    
xs = [float(x[0]) for x in rows]
ys = [float(y[1]) for y in rows]

import matplotlib.pyplot as plt

plt.scatter(xs, ys)
plt.show()