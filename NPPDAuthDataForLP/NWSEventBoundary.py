# -*- coding: utf-8 -*-
"""
Created on Wed Aug 22 13:23:32 2018

@author: WILCBM
"""
import urllib

class NWSEventBoundary:
    features = []
    
    Severities = {
            'Unknown': 0,
            'Minor': 1,
            'Moderate': 2,
            'Severe': 3,
            'Extreme': 4
            }
    
    Urgencies = {
            'Unknown': 0,
            'Future': 1,
            'Expected': 2,
            'Immediate': 3
            }
    
    Certainties = {
            'Unknown': 0,
            'Possible': 1,
            'Likely': 2,
            'Observed': 3
            }
    
    def consume(self, event, appid, rprtGrp, geom):       
        objectid = rprtGrp[rprtGrp.keys()[0]]['attributes']['OBJECTID']
        areaIds = ','.join([str(item[1]) for val in rprtGrp.values() for item in val['attributes'].items() if item[0] == 'AreaIds'])
        links = ','.join([str(item[1]) for val in rprtGrp.values() for item in val['attributes'].items() if item[0] == 'Link'])
        
        severities = [str(item[1]) for val in rprtGrp.values() for item in val['attributes'].items() if item[0] == 'Severity']
        urgencies = [str(item[1]) for val in rprtGrp.values() for item in val['attributes'].items() if item[0] == 'Urgency']
        certainties = [str(item[1]) for val in rprtGrp.values() for item in val['attributes'].items() if item[0] == 'Certainty']
        
        severity = self.Severities.keys()[self.Severities.values().index(max([self.Severities[sev] for sev in severities]))]
        urgency = self.Urgencies.keys()[self.Urgencies.values().index(max([self.Urgencies[urg] for urg in urgencies]))]
        certainty = self.Certainties.keys()[self.Certainties.values().index(max([self.Certainties[cer] for cer in certainties]))]
        category = rprtGrp[rprtGrp.keys()[0]]['attributes']['Category']
        
        start = rprtGrp[rprtGrp.keys()[0]]['attributes']['Start']
        updated = rprtGrp[rprtGrp.keys()[0]]['attributes']['Updated']
        end = rprtGrp[rprtGrp.keys()[0]]['attributes']['End']

        featureData = { 
            'attributes' : {
                'objectid': objectid,
                'event': event['data']['category'].encode('utf-8'),
                'severity': severity.encode('utf-8'),
                'summary': event['data']['summary'].encode('utf-8'),
                'link': links.encode('utf-8'),
                'urgency': urgency.encode('utf-8'),
                'certainty': certainty.encode('utf-8'),
                'category': category.encode('utf-8'),
                'updated': str(updated),
                'start': str(start),
                'end_': str(end),
                'uid': event['data']['uri'].encode('utf-8'),
                'affected': event['data']['location'].encode('utf-8'),
                'areaids': areaIds.encode('utf-8'),
                'eventid': event['data']['id'].encode('utf-8'),
                'appid': (('', appid)[appid is not None]).encode('utf-8')
                }, 
            'geometry' : geom
            }
            
        self.features = [featureData]
        
    def urlEncode(self):
        urlTuple = {
                'features' : self.features,
                'f' : 'json',
                'rollbackOnFailure' : True,
                'gdbVersion' : None
                }
        
        return urllib.urlencode(urlTuple)