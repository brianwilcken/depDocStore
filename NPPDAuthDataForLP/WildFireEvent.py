# -*- coding: utf-8 -*-
"""
Created on Fri May 04 09:22:38 2018

@author: WILCBM
"""
import time
import json

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
    
    def consume(self, reportAttr):
        self.uri = reportAttr['INTERNALID']
        self.eventDate = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(reportAttr['START_DATE'])/1000))
        self.title = 'Active fire report: ' + reportAttr['FIRE_NAME']
        self.summary = 'As of ' + time.strftime("%c", time.gmtime(int(reportAttr['REPORT_DATE'])/1000)) + ', the ' + reportAttr['FIRE_NAME'] + ' fire is estimated to be ' + str(reportAttr['AREA_']) + ' ' + reportAttr['AREA_MEAS'] + ' and ' + str(reportAttr['PER_CONT']) + '% contained.'
        self.category = 'Wildfire'
        self.latitude = str(reportAttr['LATITUDE'])
        self.longitude = str(reportAttr['LONGITUDE'])
        self.location = reportAttr['STATE']
        self.url = reportAttr['HOTLINK']
#        self.featureIds = [reportAttr['OBJECTID']]
#        if perimeterAttr is not None:
#            self.featureIds.append(perimeterAttr['OBJECTID'])
        self.userCreated = False
        self.sources = []
        self.conditionalUpdate = True
        
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)