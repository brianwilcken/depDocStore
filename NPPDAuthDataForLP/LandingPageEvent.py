# -*- coding: utf-8 -*-
"""
Created on Fri May 04 09:22:38 2018

@author: WILCBM
"""
import time
import arcpy
import json
import re
import inflect
import LandingPageEventSource
from shapely.geometry import Polygon

class LandingPageEvent:
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
    
    def consumeWildfireReport(self, rprt, perim):
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
        eventSource = LandingPageEventSource.LandingPageEventSource()
        eventSource.consumeWildfireReport(rprt)
        self.sources = [eventSource]
        self.conditionalUpdate = True
        
    def consumeNWSReport(self, rprt, geom):
        self.uri = rprt['Uid']
        self.eventDate = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(rprt['Start'])/1000))
        self.title = rprt['Event']
        self.summary = rprt['Summary']
        if 'Winter Storm' in rprt['Event']:
            self.category = 'WinterStorm'
        elif 'Flood' in rprt['Event']:
            self.category = 'Flood'
        else:
            self.category = 'LocalHazard'

        self.projectLatLon(geom)

        self.location = rprt['Affected']
        self.url = rprt['Link']
        self.userCreated = False
        eventSource = LandingPageEventSource.LandingPageEventSource()
        eventSource.consumeNWSReport(rprt)
        self.sources = [eventSource]
        self.conditionalUpdate = True
        
    def consumeHurricaneReport(self, rprt, forecast):
        p = inflect.engine()
        self.uri = rprt['STORMID']
        self.eventDate = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(rprt['STARTDTG'])/1000))
        self.title = rprt['STORMTYPE'] + " " + rprt['STORMNAME']
        if 'Hurricane' in rprt['STORMTYPE']:
            regex = re.search('\d', rprt['STORMTYPE'])
            hurricaneCat = regex.group(0)
            self.summary = 'As of ' + time.strftime("%c", time.gmtime(int(rprt['DTG'])/1000)) + ', ' + rprt['STORMNAME'] + ' is a category ' + str(hurricaneCat) + ' hurricane with wind speeds in excess of ' + str(rprt['INTENSITY']) + 'kts.  '
        else:
            self.summary = 'As of ' + time.strftime("%c", time.gmtime(int(rprt['DTG'])/1000)) + ', ' + rprt['STORMTYPE'] + " " + rprt['STORMNAME'] + ' has wind speeds in excess of ' + str(rprt['INTENSITY']) + 'kts.  '
        if rprt['INTENSITY'] < forecast['MAXWIND']:
            change = 'strengthen'
        elif rprt['INTENSITY'] >= forecast['MAXWIND']:
            change = 'weaken'
        else:
            change = 'remain unchanged'
        validDateTime = forecast['VALIDTIME'].split('/')
        validDate = int(validDateTime[0])
        validTime = validDateTime[1]
        self.summary = self.summary + 'By the ' + p.ordinal(validDate) + ' at ' + validTime + ' hours ' + forecast['TIMEZONE'] + ', ' + rprt['STORMNAME'] + ' is expected to ' + change + ', with maximum sustained winds of ' + str(forecast['MAXWIND']) + 'kts'
        gust = forecast['GUST']
        if gust > 0:
            self.summary = self.summary + ' and gusts up to ' + str(forecast['GUST']) + 'kts.'
        else:
            self.summary = self.summary + '.'
        self.category = 'Hurricane'
        self.latitude = rprt['LAT']
        self.longitude = rprt['LON']
        self.location = 'Basin: ' + rprt['BASIN']
        self.url = 'http://www.arcgis.com/home/item.html?id=5b84e159dace4bf8ad9e9154085ba45d'
        self.userCreated = False
        eventSource = LandingPageEventSource.LandingPageEventSource()
        eventSource.consumeHurricaneReport(rprt)
        self.sources = [eventSource]
        self.conditionalUpdate = True
        
    def toJSON(self):
        return json.dumps(self, default=lambda o: o.__dict__, sort_keys=True, indent=4)
    
    def projectLatLon(self, geom):
        eventCenter = Polygon(geom['rings'][0]).centroid
        
        pt = arcpy.Point()
        pt.X = eventCenter.x 
        pt.Y = eventCenter.y
        
        srIn = arcpy.SpatialReference(3857)
        srOut = arcpy.SpatialReference(4326)
        ptGeom = arcpy.PointGeometry(pt,srIn)
        proj = ptGeom.projectAs(srOut)

        self.latitude = str(proj.firstPoint.Y)
        self.longitude = str(proj.firstPoint.X)