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
import WildfireBoundary
import HurricaneBoundary
import logging

class PortalInterface:
    
    tokenHeaders = {'Content-type': 'application/x-www-form-urlencoded'}
    tokenCred = 'username=NiccAppSys&password=TrnWK9*!CbR&ip=&referer=&client=requestip&expiration=60&f=pjson'
    generateTokenUrl = 'https://cloudgis.k2geomatics.io/portal/sharing/rest/generateToken'
    serverTokenUrl = 'https://cloudgis.k2geomatics.io/portal/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Fcloudgis.k2geomatics.io%2Fserver%2Flogin%2F..%2Frest%2Fservices&referer=cloudgis.k2geomatics.io&f=json&token='
    
    eventLocationsUrl = 'https://cloudgis.k2geomatics.io/server/rest/services/Hosted/Event_Locations/FeatureServer/0'
    eventBoundariesUrl = 'https://cloudgis.k2geomatics.io/server/rest/services/Hosted/Event_Boundaries/FeatureServer/0'
    wildfireBoundariesUrl = 'http://cloudgis.k2geomatics.io/server/rest/services/Hosted/Wildfire_Boundaries/FeatureServer/0'
    hurricaneBoundariesUrl = 'http://cloudgis.k2geomatics.io/server/rest/services/Hosted/Hurricane_Boundaries/FeatureServer/0'
    
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
        
    def getEventId(self, event):
        self.indexedEventJson = json.loads(event)
        eventId = self.indexedEventJson['data']['id']
        return eventId
    
    def getPortalQuery(self, eventId):
        eventQuery = string.replace(self.portalQuery, '{eventId}', eventId)
        return eventQuery
        
    def upsertWildfireEventFeatures(self, event, report, perimeter):
        eventId = self.getEventId(event)
        appid = None
        wildfireQuery = self.getPortalQuery(eventId)
        wildfireBoundariesFs = arcpy.FeatureSet()
        wildfireBoundariesFs.load(self.wildfireBoundariesUrl + wildfireQuery)
        
        if wildfireBoundariesFs.JSON is not None:
            self.logger.info('Successfully loaded boundary data from portal for wildfire event: ' + eventId)
            wildfireBoundariesJson = json.loads(wildfireBoundariesFs.JSON)
        else:
            self.logger.error('Unable to load boundary data from portal!')
            return False
        
        if len(wildfireBoundariesJson['features']) > 0:
            #delete any old boundary data before adding the updated boundary data
            boundaries = wildfireBoundariesJson['features']
            for boundary in boundaries:
                appid_temp = boundary['attributes']['appid']
                if appid_temp is not None:
                    appid = appid_temp
                fid = boundary['attributes']['fid']
                deleteResponse = requests.post(self.wildfireBoundariesUrl + self.deleteFeatures, data='where=fid=' + str(fid) + '&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&gdbVersion=&rollbackOnFailure=true&f=json', headers=self.tokenHeaders)
                if not deleteResponse.ok:
                    self.logger.warn('Unable to delete old boundary data for wildfire event: ' + eventId)
                    
        #insert new wildfire boundaries data
        wildfireBoundary = WildfireBoundary.WildfireBoundary()
        wildfireBoundary.consume(self.indexedEventJson, appid, report['attributes'], perimeter)
        
        #POST data to portal
        portalResponse = requests.post(self.wildfireBoundariesUrl + self.addFeatures, data=wildfireBoundary.urlEncode(), headers=self.tokenHeaders)
        if portalResponse.ok:
            responseJSON = json.loads(portalResponse.content)
            success = responseJSON['addResults'][0]['success']
            if success == True:
                self.logger.info('Wildfire boundary data added for event: ' + eventId)
            else:
                self.logger.warn('Unable to add Wildfire boundary data for event: ' + eventId)
        else:
            self.logger.error('Server error (' + portalResponse.status_code + ') occurred while adding Wildfire boundary data for event: ' + eventId)
            
        return True
            
    def upsertHurricaneEventFeatures(self, event, position, forecast, pathBuffer):
        eventId = self.getEventId(event)
        appid = None
        hurricaneQuery = self.getPortalQuery(eventId)
        hurricaneBoundariesFs = arcpy.FeatureSet()
        hurricaneBoundariesFs.load(self.hurricaneBoundariesUrl + hurricaneQuery)
        
        if hurricaneBoundariesFs.JSON is not None:
            self.logger.info('Successfully loaded boundary data from portal for hurricane event: ' + eventId)
            hurricaneBoundariesJson = json.loads(hurricaneBoundariesFs.JSON)
        else:
            self.logger.error('Unable to load boundary data from portal!')
            return False
        
        if len(hurricaneBoundariesJson['features']) > 0:
            #delete any old boundary data before adding the updated boundary data
            boundaries = hurricaneBoundariesJson['features']
            for boundary in boundaries:
                appid_temp = boundary['attributes']['appid']
                if appid_temp is not None:
                    appid = appid_temp
                fid = boundary['attributes']['fid']
                deleteResponse = requests.post(self.hurricaneBoundariesUrl + self.deleteFeatures, data='where=fid=' + str(fid) + '&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&gdbVersion=&rollbackOnFailure=true&f=json', headers=self.tokenHeaders)
                if not deleteResponse.ok:
                    self.logger.warn('Unable to delete old boundary data for hurricane event: ' + eventId)
                    
        #insert new hurricane boundaries data
        hurricaneBoundary = HurricaneBoundary.HurricaneBoundary()
        hurricaneBoundary.consume(self.indexedEventJson, appid, position, forecast, pathBuffer)
        
        #POST data to portal
        portalResponse = requests.post(self.hurricaneBoundariesUrl + self.addFeatures, data=hurricaneBoundary.urlEncode(), headers=self.tokenHeaders)
        if portalResponse.ok:
            responseJSON = json.loads(portalResponse.content)
            success = responseJSON['addResults'][0]['success']
            if success == True:
                self.logger.info('Hurricane boundary data added for event: ' + eventId)
            else:
                self.logger.warn('Unable to add Hurricane boundary data for event: ' + eventId)
        else:
            self.logger.error('Server error (' + portalResponse.status_code + ') occurred while adding Hurricane boundary data for event: ' + eventId)
        return True
    
    def upsertEventFeatures(self, event, report, perimeter):
        #GET location data from portal (if it exists)
        eventId = self.getEventId(event)
        self.logger.info('Now processing location and boundary data for event: ' + eventId)
        #first perform a GET request to portal to see if the report was previously inserted
        eventQuery = self.getPortalQuery(eventId)
        eventLocationsFs = arcpy.FeatureSet()
        #Populate feature set with data (if any)
        eventLocationsFs.load(self.eventLocationsUrl + eventQuery)
        if eventLocationsFs.JSON is not None:
            self.logger.info('Successfully loaded location data from portal for event: ' + eventId)
            portalLocationsJson = json.loads(eventLocationsFs.JSON)
        else:
            self.logger.error('Unable to load location data from portal!')
            return False
        if len(portalLocationsJson['features']) > 0:
            #This feature layer already exists, so this is an update
            #only need to update the boundary data
            #first remove the pre-existing boundary data
            self.logger.info('Begin updating boundary data for event: ' + eventId)
            portalBoundariesFs = arcpy.FeatureSet()
            portalBoundariesFs.load(self.eventBoundariesUrl + eventQuery)
            portalBoundariesJson = json.loads(portalBoundariesFs.JSON)
            appid = None
            
            if len(portalBoundariesJson['features']) > 0:
                objectId = portalBoundariesJson['features'][0]['attributes']['objectid']
                appid = portalBoundariesJson['features'][0]['attributes']['appid']
                deleteResponse = requests.post(self.eventBoundariesUrl + self.deleteFeatures, data='objectIds=' + str(objectId) + '&where=&geometry=&geometryType=esriGeometryEnvelope&inSR=&spatialRel=esriSpatialRelIntersects&gdbVersion=&rollbackOnFailure=true&f=json', headers=self.tokenHeaders)
                if not deleteResponse.ok:
                    self.logger.warn('Unable to delete old boundary data for event: ' + eventId)
            
            #insert the updated boundary data
            boundaryFeature = BoundaryFeature.BoundaryFeature()
            boundaryFeature.consume(self.indexedEventJson, perimeter['geometry'], appid)
            portalResponse = requests.post(self.eventBoundariesUrl + self.addFeatures, data=boundaryFeature.urlEncode(), headers=self.tokenHeaders)
            if portalResponse.ok:
                self.logger.info('Boundary data updated for event: ' + eventId)
            else:
                self.logger.warn('Unable to update boundary data for event: ' + eventId)
        else:
            #This is a new feature layer
            #populate location and boundary objects for POST to portal
            self.logger.info('Begin adding new location and boundary data for event: ' + eventId)
            locationFeature = LocationFeature.LocationFeature()
            locationFeature.consume(self.indexedEventJson, report['geometry'])
            boundaryFeature = BoundaryFeature.BoundaryFeature()
            boundaryFeature.consume(self.indexedEventJson, perimeter['geometry'], None)
            
            #POST data to portal
            portalResponse = requests.post(self.eventLocationsUrl + self.addFeatures, data=locationFeature.urlEncode(), headers=self.tokenHeaders)
            if portalResponse.ok:
                self.logger.info('Location data added for event: ' + eventId)
                portalResponse = requests.post(self.eventBoundariesUrl + self.addFeatures, data=boundaryFeature.urlEncode(), headers=self.tokenHeaders)
                if portalResponse.ok:
                    self.logger.info('Boundary data added for event: ' + eventId)
                else:
                    self.logger.warn('Unable to add boundary data for event: ' + eventId)
            else:
                self.logger.warn('Unable to add location data for event: ' + eventId)
        return True