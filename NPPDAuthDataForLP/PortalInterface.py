# -*- coding: utf-8 -*-
"""
Created on Tue May 15 16:45:40 2018

@author: WILCBM
"""
import arcpy
import requests
import json
import string
import LocationFeature
import BoundaryFeature
import logging

class PortalInterface:
    
    tokenHeaders = {'Content-type': 'application/x-www-form-urlencoded'}
    tokenCred = 'username=brian.wilcken%40inl.gov&password=r4MQ9i6$e&ip=&referer=&client=requestip&expiration=60&f=pjson'
    generateTokenUrl = 'https://cloudgis.k2geomatics.io/portal/sharing/rest/generateToken'
    serverTokenUrl = 'https://cloudgis.k2geomatics.io/portal/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Fcloudgis.k2geomatics.io%2Fserver%2Flogin%2F..%2Frest%2Fservices&referer=cloudgis.k2geomatics.io&f=json&token='
    portalLocationsUrl = 'https://cloudgis.k2geomatics.io/server/rest/services/Hosted/Event_Locations/FeatureServer/0'
    portalBoundariesUrl = 'https://cloudgis.k2geomatics.io/server/rest/services/Hosted/Event_Boundaries/FeatureServer/0'
    
    def __init__(self):
        self.logger = logging.getLogger('authDataLogger')
        #obtain an auth token for portal
        self.logger.info('get portal token')
        response = requests.post(self.generateTokenUrl, data=self.tokenCred, headers=self.tokenHeaders)
        portalToken = json.loads(response.content)
        
        #get a portal server token
        self.logger.info('get server token')
        response = requests.get(self.serverTokenUrl + portalToken['token'], headers=self.tokenHeaders)
        serverToken = json.loads(response.content)
        serverTokenParam = 'token=' + serverToken['token']
        self.logger.info(serverTokenParam)
        
        self.portalQuery = '/query?where=eventid%3D%27{eventId}%27&&outFields=*&f=pjson&' + serverTokenParam
        self.addFeatures = '/addFeatures?' + serverTokenParam
        self.deleteFeatures = '/deleteFeatures?' + serverTokenParam
        
    def upsertEventFeatures(self, event, report, perimeter):
        #GET location data from portal (if it exists)
        indexedEventJson = json.loads(event)
        eventId = indexedEventJson['data']['id']
        self.logger.info('Now processing location and boundary data for event: ' + eventId)
        #first perform a GET request to portal to see if the report was previously inserted
        eventQuery = string.replace(self.portalQuery, '{eventId}', eventId)
        portalLocationsFs = arcpy.FeatureSet()
        #Populate feature set with data (if any)
        portalLocationsFs.load(self.portalLocationsUrl + eventQuery)
        if portalLocationsFs.JSON is not None:
            self.logger.info('Successfully loaded location data from portal for event: ' + eventId)
            portalLocationsJson = json.loads(portalLocationsFs.JSON)
        else:
            self.logger.error('Unable to load location data from portal!')
            return False
        if len(portalLocationsJson['features']) > 0:
            #This feature layer already exists, so this is an update
            #only need to update the boundary data
            #first remove the pre-existing boundary data
            self.logger.info('Begin updating boundary data for event: ' + eventId)
            portalBoundariesFs = arcpy.FeatureSet()
            portalBoundariesFs.load(self.portalBoundariesUrl + eventQuery)
            portalBoundariesJson = json.loads(portalBoundariesFs.JSON)
            appid = objectId = portalBoundariesJson['features'][0]['attributes']['appid']
            
            if len(portalBoundariesJson['features']) > 0:
                objectId = portalBoundariesJson['features'][0]['attributes']['objectid']
                deleteResponse = requests.post(self.portalBoundariesUrl + self.deleteFeatures, data='objectIds=' + str(objectId) + '&where=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&gdbVersion=&rollbackOnFailure=true&f=json', headers=self.tokenHeaders)
                if not deleteResponse.ok:
                    self.logger.warn('Unable to delete old boundary data for event: ' + eventId)
            
            #insert the updated boundary data
            boundaryFeature = BoundaryFeature.BoundaryFeature()
            boundaryFeature.consume(indexedEventJson, perimeter['geometry'], appid)
            portalResponse = requests.post(self.portalBoundariesUrl + self.addFeatures, data=boundaryFeature.urlEncode(), headers=self.tokenHeaders)
            if portalResponse.ok:
                self.logger.info('Boundary data updated for event: ' + eventId)
            else:
                self.logger.warn('Unable to update boundary data for event: ' + eventId)
        else:
            #This is a new feature layer
            #populate location and boundary objects for POST to portal
            self.logger.info('Begin adding new location and boundary data for event: ' + eventId)
            locationFeature = LocationFeature.LocationFeature()
            locationFeature.consume(indexedEventJson, report['geometry'])
            boundaryFeature = BoundaryFeature.BoundaryFeature()
            boundaryFeature.consume(indexedEventJson, perimeter['geometry'])
            
            #POST data to portal
            portalResponse = requests.post(self.portalLocationsUrl + self.addFeatures, data=locationFeature.urlEncode(), headers=self.tokenHeaders)
            if portalResponse.ok:
                self.logger.info('Location data added for event: ' + eventId)
                portalResponse = requests.post(self.portalBoundariesUrl + self.addFeatures, data=boundaryFeature.urlEncode(), headers=self.tokenHeaders)
                if portalResponse.ok:
                    self.logger.info('Boundary data added for event: ' + eventId)
                else:
                    self.logger.warn('Unable to add boundary data for event: ' + eventId)
            else:
                self.logger.warn('Unable to add location data for event: ' + eventId)
        return True