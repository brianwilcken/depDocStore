# -*- coding: utf-8 -*-
"""
Created on Mon June 04 10:37:38 2018

@author: WILCBM
"""
import time
import json

class WildFireEventSource:
    uri = ''
    eventId = ''
    articleDate = ''
    url = ''
    title = ''
    summary = ''
    sourceUri = ''
    sourceName = ''
    sourceLocation = ''
    
    def consume(self, rprt):
        self.articleDate = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(rprt['REPORT_DATE'])/1000))
        self.url = 'https://nppd.maps.arcgis.com/home/item.html?id=4ae7c683b9574856a3d3b7f75162b3f4'
        self.sourceName = rprt['SOURCE']
        self.sourceLocation = rprt['STATE']
        self.title = 'Active fire report: ' + rprt['FIRE_NAME']
        self.summary = 'Active fire report: ' + rprt['FIRE_NAME']
        
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)