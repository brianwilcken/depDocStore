
import arcpy
import requests
import json
import LandingPageEvent
import PortalInterface
import logging
from datetime import datetime
from itertools import groupby

class HurricaneEventsTracker:
    
    tokenHeaders = {'Content-type': 'application/x-www-form-urlencoded'}
    tokenCred = 'username=brian.wilcken_nppd&password=r4MQ9i6$e&ip=&referer=&client=requestip&expiration=60&f=pjson'
    generateTokenUrl='https://www.arcgis.com/sharing/rest/generateToken'
    serverTokenUrl='https://www.arcgis.com/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Flivefeeds.arcgis.com%2Farcgis%2Flogin%2F..%2Frest%2Fservices&referer=livefeeds.arcgis.com&f=json&token='
    
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

    def __init__(self, eventsServiceUrl, portalInfo):
        self.eventsServiceUrl = eventsServiceUrl
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
        self.portal = PortalInterface.PortalInterface(portalInfo)
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
        
        #load all JSON data from the ArcGIS feature sets
        self.posJson = json.loads(self.observedPositionFs.JSON)
        self.trackJson = json.loads(self.observedTrackFs.JSON)
        #self.forecastErrorConeJson = json.loads(self.forecastErrorConeFs.JSON)
        self.forecastPositionJson = json.loads(self.forecastPositionFs.JSON)
        
        self.processAuthoritativeData()
        
    def getTestData(self, stormName):
        with open('hurricane_' + stormName + '_op.json') as f:
            self.posJson = json.load(f)
            
        with open('hurricane_' + stormName + '_ot.json') as f:
            self.trackJson = json.load(f)
            
        with open('hurricane_' + stormName + '_fp.json') as f:
            self.forecastPositionJson = json.load(f)
            
        self.processAuthoritativeData()

    def processAuthoritativeData(self):
        self.logger.info('Process hurricane data')
        positions = self.posJson['features']
        tracks = self.trackJson['features']
        #forecastErrorCones = forecastErrorConeJson['features']
        forecastPositions = self.forecastPositionJson['features']

        #group all of the storm reports together under a common storm name
        self.logger.info('grouping strom reports under common name')
        stormReports = {}
        for k, v in groupby(positions, key=lambda x:x['attributes']['STORMNAME'][:]):
            stormReports[k] = list(v)
            
        #group all of the forecast reports together under a common storm name
        self.logger.info('grouping forecast reports under common name')
        forecastReports = {}
        for k, v in groupby(forecastPositions, key=lambda x:x['attributes']['STORMNAME'][:]):
            forecastReports[k] = list(v)
            
        #Get the most recent observed position for the storm
        self.logger.info('get most recent observed positions')
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
                mostRecentDT = self.getMostRecentDateTime(mostRecentPositions[stormName]['attributes'])
                seq = sorted([self.getForecastValidDateTime(x['attributes']) for x in forecastReports[stormName]])
                self.logger.info(seq)
                for forecastDT in seq:
                    self.logger.info(forecastDT+" > "+mostRecentDT)
                    if forecastDT > mostRecentDT:
                        firstForecastDT = forecastDT
                        break

                firstForecast = next((x for x in forecastReports[stormName] if self.getForecastValidDateTime(x['attributes']) == firstForecastDT), None)
                if firstForecast is not None:
                    firstForecasts[stormName] = firstForecast
            
        #flatten the set of observed tracks per storm report into a single list
        self.logger.info('flatten observed tracks per storm report into single list')
        startDates = {}
        stormTracks = {}
        for k, v in groupby(tracks, key=lambda x:x['attributes']['STORMNAME'][:]):
            stormName = k.encode('utf-8')
            trackData = list(v)
            startDates[stormName] = trackData[0]['attributes']['STARTDTG']
            if startDates[stormName] is None:
                startDates[stormName] = firstPositions[stormName]['attributes']['DTG']
            stormTracks[stormName] = [item for sublist in [d['geometry']['paths'] for d in trackData] for item in sublist]
        
        #form 100 mile buffer around observed path
        self.logger.info('form 100 mile buffer around observed path')
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
        self.logger.info('dissolve 100 mile buffer polygons into single polygon per storm')
        stormBufferFeatures = {}
        arcpy.Delete_management('in_memory')
        fileCount = 0
        for stormName in stormPathPolygons:
            fileCount += 1
            stormPathBufferFC = 'in_memory\\stormPathBuffer_featureClass_' + str(fileCount)
            simplifiedPathBufferFC = 'in_memory\\simplifiedPathBuffer_featureClass_' + str(fileCount)
            arcpy.Dissolve_management(stormPathPolygons[stormName], stormPathBufferFC, multi_part = False)
            arcpy.SimplifyPolygon_cartography(stormPathBufferFC, simplifiedPathBufferFC, tolerance=100)
            dissolvedFs = arcpy.FeatureSet()
            dissolvedFs.load(simplifiedPathBufferFC)
            #arcpy.FeaturesToJSON_conversion(stormPathBufferFC, 'output.json', 'GEOJSON')
            stormBufferFeatures[stormName] = dissolvedFs

        #iterate through the storms while posting events to the backend and to portal
        self.logger.info('post storms to backend and portal')
        for stormName in mostRecentPositions:
            position = mostRecentPositions[stormName]
            posAttr = position['attributes']
            posAttr['geometry'] = position['geometry']
            if stormName in firstForecasts and firstForecasts[stormName] is not None:
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
                        bufferGeometryPolygon = stormBufferFeatures[stormName]
                        bufferGeometryJSON = json.loads(bufferGeometryPolygon.JSON)
                        bufferGeometry = bufferGeometryJSON['features'][0]
                        self.portal.upsertHurricaneEventFeatures(response.content, posAttr, foreAttr, bufferGeometry)
                    else:
                        self.logger.warn('POST failed for event: ' + event.title)
                    
    def getMostRecentDateTime(self, mostRecentPosition):
	self.logger.info('in getMostRecentDateTime method')
        day = mostRecentPosition['DAY']
        month = mostRecentPosition['MONTH']
        try:
            mostRecentDT = str(datetime.strptime(month,'%b').month).zfill(2) + '/' + str(day).zfill(2) + '/' + str(mostRecentPosition['HHMM'])
        except:
            mostRecentDT = str(month).zfill(2) + '/' + str(day).zfill(2) + '/' + str(mostRecentPosition['HHMM'])
        return mostRecentDT
    
    def getForecastValidDateTime(self, forecastPosition):
	self.logger.info('in getForecastValidDateTime method')
        datetimestr = forecastPosition['FLDATELBL'][:-4]
        dt = datetime.strptime(datetimestr, '%Y-%m-%d %I:%M %p %a')
        forecastDT = str(dt.month).zfill(2) + '/' + forecastPosition['VALIDTIME']
        return forecastDT
        
        
                
