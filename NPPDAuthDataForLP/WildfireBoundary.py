# -*- coding: utf-8 -*-
"""
Created on Thu May 24 09:04:15 2018

@author: WILCBM
"""
import urllib

class WildfireBoundary:
    features = []
    
    def consume(self, event, appid, rprt, perim, boundary):       
        featureData = { 'attributes' : {
                'acres': perim['attributes']['ACRES'],
                'agency': (('', perim['attributes']['AGENCY'])[perim['attributes']['AGENCY'] is not None]).encode('utf-8'),
                'comments': (('', perim['attributes']['COMMENTS'])[perim['attributes']['COMMENTS'] is not None]).encode('utf-8'),
                'year_': (('', perim['attributes']['YEAR_'])[perim['attributes']['YEAR_'] is not None]).encode('utf-8'),
                'active': (('', perim['attributes']['ACTIVE'])[perim['attributes']['ACTIVE'] is not None]).encode('utf-8'),
                'unit_id': (('', perim['attributes']['UNIT_ID'])[perim['attributes']['UNIT_ID'] is not None]).encode('utf-8'),
                'fire_name': (('', perim['attributes']['FIRE_NAME'])[perim['attributes']['FIRE_NAME'] is not None]).encode('utf-8'),
                'fire_num': (('', perim['attributes']['FIRE_NUM'])[perim['attributes']['FIRE_NUM'] is not None]).encode('utf-8'),
                'fire': (('', perim['attributes']['FIRE'])[perim['attributes']['FIRE'] is not None]).encode('utf-8'),
                'inciweb_id': (('', perim['attributes']['INCIWEB_ID'])[perim['attributes']['INCIWEB_ID'] is not None]).encode('utf-8'),
                'inc_num': (('', perim['attributes']['INC_NUM'])[perim['attributes']['INC_NUM'] is not None]).encode('utf-8'),
                'load_date': str(perim['attributes']['LOAD_DATE']),
                'date_time': str(perim['attributes']['DATE_TIME']),
                'latitude': rprt['LATITUDE'],
                'longitude': rprt['LONGITUDE'],
                'area_meas': (('', rprt['AREA_MEAS'])[rprt['AREA_MEAS'] is not None]).encode('utf-8'),
                'per_cont': rprt['PER_CONT'],
                'perc_mma': (('', rprt['PERC_MMA'])[rprt['PERC_MMA'] is not None]).encode('utf-8'),
                'inc_type': (('', rprt['INC_TYPE'])[rprt['INC_TYPE'] is not None]).encode('utf-8'),
                'cause': (('', rprt['CAUSE'])[rprt['CAUSE'] is not None]).encode('utf-8'),
                'report_age': (('', rprt['REPORT_AGE'])[rprt['REPORT_AGE'] is not None]).encode('utf-8'),
                'gacc': (('', rprt['GACC'])[rprt['GACC'] is not None]).encode('utf-8'),
                'source': (('', rprt['SOURCE'])[rprt['SOURCE'] is not None]).encode('utf-8'),
                'state': (('', rprt['STATE'])[rprt['STATE'] is not None]).encode('utf-8'),
                'start_date': str(rprt['START_DATE']),
                'cont_date': str(rprt['CONT_DATE']),
                'condition': (('', rprt['CONDITION'])[rprt['CONDITION'] is not None]).encode('utf-8'),
                'wfu': (('', rprt['WFU'])[rprt['WFU'] is not None]).encode('utf-8'),
                'appid' : (('', appid)[appid is not None]).encode('utf-8'),
                'eventid' : event['data']['id'].encode('utf-8')
                }, 'geometry' : {
                        'rings' : boundary['geometry']['rings']
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