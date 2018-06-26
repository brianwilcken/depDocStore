
import arcpy
import requests
import json
import LandingPageEvent
import PortalInterface
import logging

class NWSEventsTracker:
    
    tokenHeaders = {'Content-type': 'application/x-www-form-urlencoded'}
    tokenCred = 'username=brian.wilcken_nppd&password=r4MQ9i6$e&ip=&referer=&client=requestip&expiration=60&f=pjson'
    generateTokenUrl='https://www.arcgis.com/sharing/rest/generateToken'
    serverTokenUrl='https://www.arcgis.com/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Flivefeeds.arcgis.com%2Farcgis%2Flogin%2F..%2Frest%2Fservices&referer=livefeeds.arcgis.com&f=json&token='
    
    #Java NLP service
    eventsServiceUrl = 'http://localhost:8080/eventNLP/api/events'
    requestHeaders = {'Content-type': 'application/json'}

    #NPPD NWS Feature Service
    eventsBySizeSeverityUrl = 'https://livefeeds.arcgis.com/arcgis/rest/services/LiveFeeds/NWS_Watches_Warnings_and_Advisories/MapServer/6'
    eventsBySizeSeverityQuery = '/query?where=%28Event+LIKE+%27%25Warning%27+OR+Event+%3D+%27Flash+Flood+Watch%27%29+AND+Event+NOT+IN+%28%27Dust+Storm+Warning%27%2C+%27Hurricane+Warning%27%2C+%27Excessive+Heat+Warning%27%2C+%27Extreme+Cold+Warning%27%2C+%27Fire+Warning%27%2C+%27Freeze+Warning%27%2C+%27Gale+Warning%27%2C+%27Hard+Freeze+Warning%27%2C+%27Hazardous+Seas+Warning%27%2C+%27Heavy+Freezing+Spray+Warning%27%2C+%27High+Surf+Warning%27%2C+%27Red+Flag+Warning%27%2C+%27Severe+Thunderstorm+Warning%27%2C+%27Shelter+in+Place+Warning%27%2C+%27Special+Marine+Warning%27%2C+%27Storm+Warning%27%2C+%27Wind+Chill+Warning%27%29&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=*&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&queryByDistance=&returnExtentsOnly=false&datumTransformation=&parameterValues=&rangeValues=&f=pjson&token='

    def __init__(self):
        self.logger = logging.getLogger('authDataLogger')
        
        #obtain an auth token for arcgis
        self.logger.info('get arcgis token')
        response = requests.post(self.generateTokenUrl, data=self.tokenCred, headers=self.tokenHeaders)
        arcgisToken = json.loads(response.content)
        
        #get an arcgis server token
        self.logger.info('get server token')
        response = requests.get(self.serverTokenUrl + arcgisToken['token'], headers=self.tokenHeaders)
        serverToken = json.loads(response.content)
        self.serverTokenParam = serverToken['token']
        self.logger.info(self.serverTokenParam)
        
        self.logger.info('Begin instantiate portal interface')
        self.portal = PortalInterface.PortalInterface()
        self.logger.info('End instantiate portal interface')

    def getAuthoritativeData(self):
        self.logger.info('Load wildfire data')
        #Load active fire data from arcgis
        self.nwsReportsFs = arcpy.FeatureSet()
        self.nwsReportsFs.load(self.eventsBySizeSeverityUrl + self.eventsBySizeSeverityQuery + self.serverTokenParam)
        self.processAuthoritativeData()

    def processAuthoritativeData(self):
        self.logger.info('Process wildfire data')
        reportJson = json.loads(self.nwsReportsFs.JSON)
        reports = reportJson['features']

        for report in reports:
            reportAttr = report['attributes']
            geom = report['geometry']
            #initialize event object to be POSTed to the event service
            event = LandingPageEvent.LandingPageEvent()
            event.consumeNWSReport(reportAttr, geom)
#            
#            self.logger.info('POST wildfire event to backend: ' + event.title)
#            response = requests.post(self.eventsServiceUrl, data=event.toJSON(), headers=self.requestHeaders)
#            if response.ok:
#                self.logger.info('POST successful for event: ' + event.title)
#                #self.portal.upsertNWSEventFeatures(response.content, report)
#            else:
#                self.logger.warn('POST failed for event: ' + event.title)
                