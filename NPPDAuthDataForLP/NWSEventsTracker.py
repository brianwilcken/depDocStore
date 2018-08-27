
import arcpy
import requests
import json
import LandingPageEvent
import PortalInterface
import logging
from itertools import groupby

class NWSEventsTracker:
    
    tokenHeaders = {'Content-type': 'application/x-www-form-urlencoded'}
    tokenCred = 'username=brian.wilcken_nppd&password=r4MQ9i6$e&ip=&referer=&client=requestip&expiration=60&f=pjson'
    generateTokenUrl='https://www.arcgis.com/sharing/rest/generateToken'
    serverTokenUrl='https://www.arcgis.com/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Flivefeeds.arcgis.com%2Farcgis%2Flogin%2F..%2Frest%2Fservices&referer=livefeeds.arcgis.com&f=json&token='
    
    requestHeaders = {'Content-type': 'application/json'}

    #NPPD NWS Feature Service
    eventsBySizeSeverityUrl = 'https://livefeeds.arcgis.com/arcgis/rest/services/LiveFeeds/NWS_Watches_Warnings_and_Advisories/MapServer/6'
    eventsBySizeSeverityQuery = '/query?where=Event+LIKE+%27%25Warning%27+AND+Event+NOT+IN+%28%27Dust+Storm+Warning%27%2C+%27Hurricane+Warning%27%2C+%27Excessive+Heat+Warning%27%2C+%27Extreme+Cold+Warning%27%2C+%27Fire+Warning%27%2C+%27Freeze+Warning%27%2C+%27Gale+Warning%27%2C+%27Hard+Freeze+Warning%27%2C+%27Hazardous+Seas+Warning%27%2C+%27Heavy+Freezing+Spray+Warning%27%2C+%27High+Surf+Warning%27%2C+%27Red+Flag+Warning%27%2C+%27Severe+Thunderstorm+Warning%27%2C+%27Shelter+in+Place+Warning%27%2C+%27Special+Marine+Warning%27%2C+%27Storm+Warning%27%2C+%27Wind+Chill+Warning%27%29&text=&objectIds=&time=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&relationParam=&outFields=*&returnGeometry=true&returnTrueCurves=false&maxAllowableOffset=&geometryPrecision=&outSR=&returnIdsOnly=false&returnCountOnly=false&orderByFields=&groupByFieldsForStatistics=&outStatistics=&returnZ=false&returnM=false&gdbVersion=&returnDistinctValues=false&resultOffset=&resultRecordCount=&queryByDistance=&returnExtentsOnly=false&datumTransformation=&parameterValues=&rangeValues=&f=pjson&token='

    NWSEventMapping = {
            'Hazardous Materials Warning':	'ChemicalSpill',
            'Earthquake Warning':	'Earthquake',
            'Coastal Flood Warning':	'Flood',
            'Flash Flood Warning':	'Flood',
            'Flood Warning':	'Flood',
            'Lakeshore Flood Warning':	'Flood',
            'Tsunami Warning':	'Flood',
            'Hurricane Force Wind Warning':	'Hurricane',
            'Hurricane Warning':	'Hurricane',
            'Tropical Storm Warning':	'Hurricane',
            'Typhoon Warning':	'Hurricane',
            'Avalanche Warning':	'LocalHazard',
            'Civil Danger Warning':	'LocalHazard',
            'Extreme Wind Warning':	'LocalHazard',
            'High Wind Warning':	'LocalHazard',
            'Law Enforcement Warning':	'LocalHazard',
            'Tornado Warning':'LocalHazard',
            'Volcano Warning':'LocalHazard',
            'Nuclear Power Plant Warning':	'Nuclear',
            'Radiological Hazard Warning':	'Nuclear',
            'Blizzard Warning':	'WinterStorm',
            'Ice Storm Warning':	'WinterStorm',
            'Lake Effect Snow Warning':	'WinterStorm',
            'Winter Storm Warning':	'WinterStorm'
            }

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
        self.logger.info('Load wildfire data')
        #Load active fire data from arcgis
        self.nwsReportsFs = arcpy.FeatureSet()
        self.nwsReportsFs.load(self.eventsBySizeSeverityUrl + self.eventsBySizeSeverityQuery + self.serverTokenParam)
        self.processAuthoritativeData()

    def processAuthoritativeData(self):
        self.logger.info('Process wildfire data')
        reportJson = json.loads(self.nwsReportsFs.JSON)
        reports = reportJson['features']

#        with open('data.json', 'w') as outfile:
#            json.dump(reports, outfile)

        #1. Group together NWS reports into common event types
        commonTypeReports = {}
        for k, v in groupby(reports, key=lambda x:x['attributes']['Event'][:]):
            category = self.NWSEventMapping[k]
            if commonTypeReports.has_key(category):
                commonTypeReports[category] = commonTypeReports[category] + list(v)
            else:
                commonTypeReports[self.NWSEventMapping[k]] = list(v)

        #2. For each event type find which ones are touching and call those a grouped event
        for group in commonTypeReports:
            #generate an arcpy polygon for each report boundary
            for report in commonTypeReports[group]:
                boundaries = [boundary for boundary in report['geometry']['rings']]
                boundary_polygons = [arcpy.Polygon(arcpy.Array([arcpy.Point(*coords) for coords in boundary]), arcpy.SpatialReference(3857)) for boundary in boundaries]
                report['boundary_polygons'] = boundary_polygons
            #generate subsets of polygons that touch each other - these subsets will be grouped into common events
            subGroups = []
            for report in commonTypeReports[group]:
                #each subset is considered as 1st level contiguous polygons 
                subGroup = {}
                subGroup[report['attributes']['Uid']] = report;
                for otherReport in commonTypeReports[group]:
                    overlap = any([boundaryPolygon.touches(otherBoundaryPolygon) for boundaryPolygon in report['boundary_polygons'] for otherBoundaryPolygon in otherReport['boundary_polygons']])
                    if overlap:
                        subGroup[otherReport['attributes']['Uid']] = otherReport;
                subGroups.append(subGroup)
            #transitive relations between nth level contiguous polygons will be merged to form a fully defined boundary for each event
            eventGroups = []
            for subGroup in subGroups:
                eventGroup = []
                eventGroup.append(subGroup)
                if len(subGroup.keys()) > 1:
                    for otherSubGroup in subGroups:
                        if set.intersection(set(subGroup.keys()), set(otherSubGroup.keys())):
                            eventGroup.append(otherSubGroup)
                eventGroups.append(eventGroup)
                #subGroups.remove(subGroup)
            #Each event group may contain duplicates, due to the nature of the intersection process.  These duplicates will be removed to form unique event groups.
            uniqueGroups = {}
            for eventGroup in eventGroups:
                uniqueGroup = {}
                for subGroup in eventGroup:
                    uniqueGroup.update(subGroup)
                uniqueGroups[max(uniqueGroup.keys())] = uniqueGroup
            #Convert the dictionary of unique groups into a finalized list of groups.  This list will be used in the boundary dissolve process.
            finalizedGroups = uniqueGroups.values()
        
            #3. For each grouped event dissolve and simplify all boundaries into a single common boundary
            dissolvedGroups = []
            arcpy.Delete_management('in_memory')
            groupCount = 0
            for finalizedGroup in finalizedGroups:
                dissolvedGroup = {}
                dissolvedGroup['groupedEvents'] = finalizedGroup
                groupCount += 1
                boundaryPolygons = [boundaryPolygon for partialBoundary in finalizedGroup.values() for boundaryPolygon in partialBoundary['boundary_polygons']]
                dissolveBoundaryFC = 'in_memory\\dissolveBoundary_featureClass_' + str(groupCount)
                simplifyBoundaryFC = 'in_memory\\simplifyBoundary_featureClass_' + str(groupCount)
                arcpy.Dissolve_management(boundaryPolygons, dissolveBoundaryFC, multi_part = False)
                arcpy.SimplifyPolygon_cartography(dissolveBoundaryFC, simplifyBoundaryFC, tolerance=100)
                dissolvedBoundaryFS = arcpy.FeatureSet()
                dissolvedBoundaryFS.load(simplifyBoundaryFC)
                boundaryData = json.loads(dissolvedBoundaryFS.JSON)
                dissolvedGroup['boundary'] = [feature['geometry'] for feature in boundaryData['features']]
                dissolvedGroups.append(dissolvedGroup)

            #4. For each grouped event determine if the event represents a new unique incident or if a previolusly indexed incident
            #must be updated through merging together all (old and new) boundary data.  This will help to keep track of the total geographic
            #extent reached by an incident.  NOTE: this step will be performed within the Java backend.
            for dissolvedGroup in dissolvedGroups:
                groupedEvents = dissolvedGroup['groupedEvents']
                geom = dissolvedGroup['boundary']
                #initialize event object to be POSTed to the event service
                event = LandingPageEvent.LandingPageEvent()
                event.consumeNWSReportGroup(groupedEvents, geom, group)
                
                self.logger.info('POST NWS event to backend: ' + event.title)
                response = requests.post(self.eventsServiceUrl, data=event.toJSON(), headers=self.requestHeaders)
                if response.ok:
                    self.logger.info('POST successful for event: ' + event.title)
                    self.portal.upsertNWSEventFeatures(response.content, groupedEvents, geom)
                else:
                    self.logger.warn('POST failed for event: ' + event.title)