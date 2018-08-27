import sys
import arcpy
import requests
import json
import LandingPageEvent
import PortalInterface
import logging
import string

class WildFireEventsTracker:

    requestHeaders = {'Content-type': 'application/json'}

    #NPPD Wildfire Feature Service
    wildFireActivityUrl = 'https://utility.arcgis.com/usrsvcs/servers/ab338b46b77b4765b0a04e883de77db6/rest/services/LiveFeeds/Wildfire_Activity/MapServer/'
    activeFireLayer = '0'
    activePerimeterLayer = '2'
    wildFireActivityQuery = '/query?f=pjson&where=1%3D1&returnGeometry=true&spatialRel=esriSpatialRelIntersects&outFields=*&orderByFields=OBJECTID%20ASC&outSR=102100'
    wildFirePerimeterQuery = '/query?f=pjson&where=FIRE_NAME%3D%27{fireName}%27&returnGeometry=true&spatialRel=esriSpatialRelIntersects&outFields=*&orderByFields=OBJECTID%20ASC&outSR=102100'

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
        self.wildfireReportsFs.load(self.wildFireActivityUrl + self.activeFireLayer + self.wildFireActivityQuery)
        self.processAuthoritativeData()

    def processAuthoritativeData(self):
        self.logger.info('Process wildfire data')
        reportJson = json.loads(self.wildfireReportsFs.JSON)
        reports = reportJson['features']

        for report in reports:
            reportAttr = report['attributes']
            
            if not reportAttr.has_key('AREA_') or reportAttr['AREA_'] is None or reportAttr['AREA_'] < 10000: #10,000 acre minimum size
                self.logger.info('Skipping due to small size for wildfire: ' + reportAttr['FIRE_NAME'])
                continue

            if reportAttr['START_DATE'] is not None and reportAttr['REPORT_DATE'] is not None:
                #get perimeter data for report
                perimeterQuery = string.replace(self.wildFirePerimeterQuery, '{fireName}', reportAttr['FIRE_NAME'])
                try:
                    self.logger.info('load boundary data for wildfire: ' + reportAttr['FIRE_NAME'])
                    perimeterFs = arcpy.FeatureSet()
                    perimeterFs.load(self.wildFireActivityUrl + self.activePerimeterLayer + perimeterQuery)
                except:
                    self.logger.error('Unable to load boundary data for wildfire: ' + reportAttr['FIRE_NAME'] + ' exception: ' + str(sys.exc_info()[0]))
                    continue
                
                perimeterJson = json.loads(perimeterFs.JSON)
                if not perimeterJson.has_key('features') or len(perimeterJson['features']) == 0 or not perimeterJson['features'][0].has_key('attributes'):
                    self.logger.info('Skipping due to incomplete boundary data for wildfire: ' + reportAttr['FIRE_NAME'])
                    continue
                
                latestFeature = len(perimeterJson['features']) - 1
                perimeter = perimeterJson['features'][latestFeature]
                perimeterAttr = perimeter['attributes']
                
                if perimeterAttr is not None:
                    #initialize event object to be POSTed to the event service
                    try:
                        event = LandingPageEvent.LandingPageEvent()
                        event.consumeWildfireReport(reportAttr, perimeterAttr)
                        self.logger.info('successfully created wildfire event from report data')
                    except:
                        self.logger.error('failed to produce wildfire event from report data: ' + str(sys.exc_info()[0]))
                        continue
                    
                    self.logger.info('POST wildfire event to backend: ' + event.title)
                    response = requests.post(self.eventsServiceUrl, data=event.toJSON(), headers=self.requestHeaders)
                    if response.ok:
                        self.logger.info('POST successful for event: ' + event.title)
                        try:
                            self.logger.info('send wildfire data to portal: ' + event.uri)
                            self.portal.upsertWildfireEventFeatures(response.content, report, perimeter)
                            self.logger.info('wildfire data successfully sent to portal: ' + event.uri)
                        except:
                            self.logger.error('failed to send wildfire data to portal: ' + event.uri + ' exception: ' + str(sys.exc_info()[0]))
                    else:
                        self.logger.warn('POST failed for event: ' + event.title + ' exception: ' + str(sys.exc_info()[0]))