
import arcpy
import requests
import json
import LandingPageEvent
import PortalInterface
import logging
from itertools import groupby

class HurricaneEventsTracker:
    
    tokenHeaders = {'Content-type': 'application/x-www-form-urlencoded'}
    tokenCred = 'username=brian.wilcken_nppd&password=r4MQ9i6$e&ip=&referer=&client=requestip&expiration=60&f=pjson'
    generateTokenUrl='https://www.arcgis.com/sharing/rest/generateToken'
    serverTokenUrl='https://www.arcgis.com/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Flivefeeds.arcgis.com%2Farcgis%2Flogin%2F..%2Frest%2Fservices&referer=livefeeds.arcgis.com&f=json&token='
    
    #Java NLP service
    eventsServiceUrl = 'http://localhost:8080/eventNLP/api/events'
    requestHeaders = {'Content-type': 'application/json'}

    #NPPD Hurricane Feature Service
    activeHurricaneUrl = 'https://livefeeds.arcgis.com/arcgis/rest/services/LiveFeeds/Hurricane_Active/MapServer/'
    #Event Location
    forecastPositionLayer = '0'
    observedPositionLayer = '1'
    #Event Boundary
    observedTrackLayer = '3'
    #forecastErrorConeLayer = '4'
    
    observedPositionQuery = '/query?where=1%3D1&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=*&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=STORMNAME+DESC%2C+DTG+DESC&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&queryByDistance=&returnExtentsOnly=false&datumTransformation=&parameterValues=&rangeValues='
    observedTrackQuery = '/query?where=1%3D1&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=*&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=STORMNAME+DESC%2C+ENDDTG+DESC&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&queryByDistance=&returnExtentsOnly=false&datumTransformation=&parameterValues=&rangeValues='
    #forecastErrorConeQuery = '/query?where=1%3D1&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=*&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&queryByDistance=&returnExtentsOnly=false&datumTransformation=&parameterValues=&rangeValues='
    forecastPositionQuery = '/query?where=1%3D1&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=*&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&queryByDistance=&returnExtentsOnly=false&datumTransformation=&parameterValues=&rangeValues='

    querySuffix = '&f=pjson&token='
    
    metersPerMile = 1609.34

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
        self.logger.info('Load hurricane data')
        #Load active hurricane data from arcgis
        self.observedPositionFs = arcpy.FeatureSet()
        self.observedPositionFs.load(self.activeHurricaneUrl + self.observedPositionLayer + self.observedPositionQuery + self.querySuffix + self.serverTokenParam)
        self.observedTrackFs = arcpy.FeatureSet()
        self.observedTrackFs.load(self.activeHurricaneUrl + self.observedTrackLayer + self.observedTrackQuery + self.querySuffix + self.serverTokenParam)
        #self.forecastErrorConeFs = arcpy.FeatureSet()
        #self.forecastErrorConeFs.load(self.activeHurricaneUrl + self.forecastErrorConeLayer + self.forecastErrorConeQuery + self.querySuffix + self.serverTokenParam)
        self.forecastPositionFs = arcpy.FeatureSet()
        self.forecastPositionFs.load(self.activeHurricaneUrl + self.forecastPositionLayer + self.forecastPositionQuery + self.querySuffix + self.serverTokenParam)
        self.processAuthoritativeData()

    def processAuthoritativeData(self):
        self.logger.info('Process hurricane data')
        posJson = json.loads(self.observedPositionFs.JSON)
        positions = posJson['features']
        trackJson = json.loads(self.observedTrackFs.JSON)
        tracks = trackJson['features']
        #forecastErrorConeJson = json.loads(self.forecastErrorConeFs.JSON)
        #forecastErrorCones = forecastErrorConeJson['features']
        forecastPositionJson = json.loads(self.forecastPositionFs.JSON)
        forecastPositions = forecastPositionJson['features']

        #group all of the storm reports together under a common storm name
        stormReports = {}
        for k, v in groupby(positions, key=lambda x:x['attributes']['STORMNAME'][:]):
            stormReports[k] = list(v)
            
        #group all of the forecast reports together under a common storm name
        forecastReports = {}
        for k, v in groupby(forecastPositions, key=lambda x:x['attributes']['STORMNAME'][:]):
            forecastReports[k] = list(v)
            
        #Get the most recent observed position for the storm
        mostRecentPositions = {}
        firstPositions = {}
        firstForecasts = {}
        for stormName in stormReports:
            seq = [str(x['attributes']['DTG']) for x in stormReports[stormName]]
            index, value = max(enumerate(seq))
            mostRecentPositions[stormName] = stormReports[stormName][index]
            index, value = min(enumerate(seq))
            firstPositions[stormName] = stormReports[stormName][index]
            #Get the first forecast report with a date/time greater than the most recent observed position
            if forecastReports[stormName] is not None and len(forecastReports[stormName]) > 0:
                mostRecentDT = str(mostRecentPositions[stormName]['attributes']['DAY']) + '/' + str(mostRecentPositions[stormName]['attributes']['HHMM'])
                seq = sorted([x['attributes']['VALIDTIME'] for x in forecastReports[stormName]])
                for forecastDT in seq:
                    if forecastDT > mostRecentDT:
                        firstForecastDT = forecastDT
                        break

                firstForecast = next((x for x in forecastReports[stormName] if x['attributes']['VALIDTIME'] == firstForecastDT), None)
                firstForecasts[stormName] = firstForecast
            
        #flatten the set of observed tracks per storm report into a single list
        startDates = {}
        stormTracks = {}
        for k, v in groupby(tracks, key=lambda x:x['attributes']['STORMNAME'][:]):
            trackData = list(v)
            startDates[k] = trackData[0]['attributes']['STARTDTG']
            if startDates[k] is None:
                startDates[k] = firstPositions[k]['attributes']['DTG']
            stormTracks[k] = [item for sublist in [d['geometry']['paths'] for d in trackData] for item in sublist]
            #stormTracks[k] = [item for sublist in [item for sublist in [d['geometry']['paths'] for d in trackData] for item in sublist] for item in sublist]
        

        #form 100 mile buffer around observed path
        stormPathPolygons = {}
        stormPathPolylines = {}
        for stormName in stormTracks:
            pathPolylines = []
            pathBuffers = []
            for path in stormTracks[stormName]:
                pathPolyline = arcpy.Polyline(arcpy.Array([arcpy.Point(*coords) for coords in path]), arcpy.SpatialReference(3857))
                bufferedPath = pathPolyline.buffer(self.metersPerMile * 100) #100 mile buffer
                pathPolylines.append(pathPolyline)
                pathBuffers.append(bufferedPath)
            stormPathPolylines[stormName] = pathPolylines
            stormPathPolygons[stormName] = pathBuffers
        
        #Dissolve 100 mile buffer polygons into single polygon per storm
        stormBufferFeatures = {}
        arcpy.Delete_management('in_memory')
        for stormName in stormPathPolygons:
            stormPathBufferFC = 'in_memory\\' + stormName + '_stormPathBuffer_featureClass'
            arcpy.Dissolve_management(stormPathPolygons[stormName], stormPathBufferFC)
            dissolvedFs = arcpy.FeatureSet()
            dissolvedFs.load(stormPathBufferFC)
            stormBufferFeatures[stormName] = dissolvedFs

        #iterate through the storms whiule posting events to the backend and to portal
        for stormName in mostRecentPositions:
            position = mostRecentPositions[stormName]
            posAttr = position['attributes']
            posAttr['geometry'] = position['geometry']
            foreAttr = firstForecasts[stormName]['attributes']
            if startDates.has_key(posAttr['STORMNAME']) and startDates[posAttr['STORMNAME']] is not None:
                posAttr['STARTDTG'] = startDates[stormName]
                #initialize event object to be POSTed to the event service
                event = LandingPageEvent.LandingPageEvent()
                event.consumeHurricaneReport(posAttr, foreAttr)
                
                self.logger.info('POST hurricane event to backend: ' + event.title)
                response = requests.post(self.eventsServiceUrl, data=event.toJSON(), headers=self.requestHeaders)
                if response.ok:
                    self.logger.info('POST successful for event: ' + event.title)
                    bufferGeometryJSON = json.loads(stormBufferFeatures[stormName].JSON)
                    bufferGeometry = bufferGeometryJSON['features'][0]
                    self.portal.upsertHurricaneEventFeatures(response.content, posAttr, foreAttr, bufferGeometry)
                else:
                    self.logger.warn('POST failed for event: ' + event.title)
                