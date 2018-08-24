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
    nwsEvent = False
    
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
        
    def consumeNWSReportGroup(self, rprtGrp, geoms, category):
#        ids = [str(attr[1]) for val in rprtGrp.values() for attr in val['attributes'].items() if attr[0] == 'OBJECTID']
#        ids.sort()
        Uids = [str(item[0]) for item in rprtGrp.items()]
        self.uri = ','.join(Uids)
        self.eventDate = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(min([attr['attributes']['Start'] for attr in rprtGrp.values()]))/1000))
        self.category = category
        self.projectLatLon(geoms)
        affectedLocations = list(set([loc for val in rprtGrp.values() for loc in str(val['attributes']['Affected']).split('; ')]))
        affectedLocations.sort()
        self.location = ', '.join(affectedLocations)
        self.title = 'National Weather Service: ' + rprtGrp[rprtGrp.keys()[0]]['attributes']['Event'] + " (Urgency: " + rprtGrp[rprtGrp.keys()[0]]['attributes']['Urgency'] + ", Severity: " + rprtGrp[rprtGrp.keys()[0]]['attributes']['Severity'] + ")"
        self.summary = rprtGrp[rprtGrp.keys()[0]]['attributes']['Summary']
        self.userCreated = False
        urls = list(set([str(item[1]) for val in rprtGrp.values() for item in val['attributes'].items() if item[0] == 'Link']))
        self.sources = []
        for url in urls:
            eventSource = LandingPageEventSource.LandingPageEventSource()
            eventSource.consumeNWSReportGroup(self.title, self.summary, self.eventDate, url)
            self.sources.append(eventSource)
        self.conditionalUpdate = True
        self.nwsEvent = True
        
    def consumeHurricaneReport(self, rprt, forecast):
        p = inflect.engine()
        self.uri = rprt['STORMID']
        self.eventDate = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(rprt['STARTDTG'])/1000))
        self.title = " ".join(re.findall("[a-zA-Z]+", rprt['STORMTYPE'])) + " " + rprt['STORMNAME']
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
    
    def projectLatLon(self, geoms):
        geometry = [coords for geom in geoms for coords in geom['rings'][0]]
        eventCenter = Polygon(geometry).centroid
        
        pt = arcpy.Point()
        pt.X = eventCenter.x 
        pt.Y = eventCenter.y
        
        srIn = arcpy.SpatialReference(3857)
        srOut = arcpy.SpatialReference(4326)
        ptGeom = arcpy.PointGeometry(pt,srIn)
        proj = ptGeom.projectAs(srOut)

        self.latitude = str(proj.firstPoint.Y)
        self.longitude = str(proj.firstPoint.X)