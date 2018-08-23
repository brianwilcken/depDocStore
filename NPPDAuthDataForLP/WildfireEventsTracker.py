
import arcpy
import requests
import json
import LandingPageEvent
import PortalInterface
import logging

class WildFireEventsTracker:

    requestHeaders = {'Content-type': 'application/json'}

    #NPPD Wildfire Feature Service
    wildFireActivityUrl = 'https://utility.arcgis.com/usrsvcs/servers/ab338b46b77b4765b0a04e883de77db6/rest/services/LiveFeeds/Wildfire_Activity/MapServer/'
    activeFireLayer = '0'
    activePerimeterLayer = '2'
    wildFireActivityQuery = '/query?f=pjson&where=1%3D1&returnGeometry=true&spatialRel=esriSpatialRelIntersects&outFields=*&orderByFields=OBJECTID%20ASC&outSR=102100'

    def __init__(self, eventsServiceUrl, portalInfo):
        #Java NLP service
        self.eventsServiceUrl = eventsServiceUrl
        self.logger = logging.getLogger('authDataLogger')
        self.logger.info('Begin instantiate portal interface')
        self.portal = PortalInterface.PortalInterface(portalInfo)
        self.logger.info('End instantiate portal interface')

    def getAuthoritativeData(self):
        self.logger.info('Load wildfire data')
        #Load active fire data from arcgis
        self.wildfireReportsFs = arcpy.FeatureSet()
        self.wildfirePerimetersFs = arcpy.FeatureSet()
        self.wildfireReportsFs.load(self.wildFireActivityUrl + self.activeFireLayer + self.wildFireActivityQuery)
        self.wildfirePerimetersFs.load(self.wildFireActivityUrl + self.activePerimeterLayer + self.wildFireActivityQuery)
        self.processAuthoritativeData()

    def processAuthoritativeData(self):
        self.logger.info('Process wildfire data')
        reportJson = json.loads(self.wildfireReportsFs.JSON)
        perimeterJson = json.loads(self.wildfirePerimetersFs.JSON)
        reports = reportJson['features']
        perimeters = perimeterJson['features']

        for report in reports:
            reportAttr = report['attributes']
            
            if reportAttr['START_DATE'] is not None and reportAttr['REPORT_DATE'] is not None:
                for perimeter in perimeters:
                    perimeterAttr = perimeter['attributes']
                    if perimeterAttr['FIRE_NAME'] == reportAttr['FIRE_NAME']:
                        break
                    else:
                        perimeterAttr = None
                
                if perimeterAttr is not None:
                    #initialize event object to be POSTed to the event service
                    event = LandingPageEvent.LandingPageEvent()
                    event.consumeWildfireReport(reportAttr, perimeterAttr)
                    
                    self.logger.info('POST wildfire event to backend: ' + event.title)
                    response = requests.post(self.eventsServiceUrl, data=event.toJSON(), headers=self.requestHeaders)
                    if response.ok:
                        self.logger.info('POST successful for event: ' + event.title)
                        self.portal.upsertWildfireEventFeatures(response.content, report, perimeter)
                    else:
                        self.logger.warn('POST failed for event: ' + event.title)