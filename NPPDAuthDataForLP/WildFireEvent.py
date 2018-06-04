# -*- coding: utf-8 -*-
"""
Created on Fri May 04 09:22:38 2018

@author: WILCBM
"""
import time
import json
import WildFireEventSource

class WildFireEvent:
    uri = ''
    eventDate = ''
    title = ''
    summary = ''
    category = ''
    latitude = ''
    longitude = ''
    location = ''
    url = ''
    featureIds = []
    totalArticleCount = 0
    userCreated = False
    sources = []
    conditionalUpdate = True
    
    def consume(self, rprt, perim):
        self.uri = rprt['INTERNALID']
        self.eventDate = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(rprt['START_DATE'])/1000))
        self.title = 'Active fire report: ' + rprt['FIRE_NAME']
        self.summary = 'As of ' + time.strftime("%c", time.gmtime(int(rprt['REPORT_DATE'])/1000)) + ', the ' + rprt['FIRE_NAME'] + ' fire is estimated to be ' + str(round(perim['ACRES'], 2)) + ' ' + rprt['AREA_MEAS'] + ' and ' + str(rprt['PER_CONT']) + '% contained.'
        self.category = 'Wildfire'
        self.latitude = str(rprt['LATITUDE'])
        self.longitude = str(rprt['LONGITUDE'])
        self.location = rprt['STATE']
        self.url = rprt['HOTLINK']
#        self.featureIds = [rprt['OBJECTID']]
#        if perimeterAttr is not None:
#            self.featureIds.append(perimeterAttr['OBJECTID'])
        self.userCreated = False
        eventSource = WildFireEventSource.WildFireEventSource()
        eventSource.consume(rprt)
        self.sources = [eventSource]
        self.conditionalUpdate = True
        
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)