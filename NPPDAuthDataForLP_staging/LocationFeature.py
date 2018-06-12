# -*- coding: utf-8 -*-
"""
Created on Tue May 08 08:56:46 2018

@author: WILCBM
"""
import urllib

class LocationFeature:
    features = []
    
    def consume(self, eventJson, geometry):
        featureData = { 'attributes' : {
                'eventid' : eventJson['data']['id'].encode('utf-8'),
                'location': eventJson['data']['location'].encode('utf-8')
                }, 'geometry' : {
                        'x' : geometry['x'],
                        'y' : geometry['y']
                        } }
        self.features = [featureData]
        
    def urlEncode(self):
        urlTuple = {
                'features' : self.features,
                'f' : 'json',
                'rollbackOnFailure' : True,
                'gdbVersion' : None
                }
        
        return urllib.urlencode(urlTuple)