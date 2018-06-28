# -*- coding: utf-8 -*-
"""
Created on Thu May 24 09:04:15 2018

@author: WILCBM
"""
import urllib

class HurricaneBoundary:
    features = []
    
    def consume(self, event, appid, pos, fore, buff):       
        featureData = { 'attributes' : {
                'stormname': (('', pos['STORMNAME'])[pos['STORMNAME'] is not None]).encode('utf-8'),
                'o_dtg': str(pos['DTG']),
                'o_year': pos['YEAR'],
                'o_month': (('', pos['MONTH'])[pos['MONTH'] is not None]).encode('utf-8'),
                'o_day': pos['DAY'],
                'o_hhmm': (('', pos['HHMM'])[pos['HHMM'] is not None]).encode('utf-8'),
                'o_tau': pos['TAU'],
                'o_mslp': pos['MSLP'],
                'o_basin': (('', pos['BASIN'])[pos['BASIN'] is not None]).encode('utf-8'),
                'o_stormnum': pos['STORMNUM'],
                'o_stormtyp': (('', pos['STORMTYPE'])[pos['STORMTYPE'] is not None]).encode('utf-8'),
                'o_intensit': pos['INTENSITY'],
                'o_ss': pos['SS'],
                'o_lat': pos['LAT'],
                'o_lon': pos['LON'],
                'f_advdate': str(fore['ADVDATE']),
                #'f_advisnum': ((None, int(fore['ADVISNUM']))[fore['ADVISNUM'] is not None]),
                'f_fcstprd': fore['FCSTPRD'],
                'f_basin': (('', fore['BASIN'])[fore['BASIN'] is not None]).encode('utf-8'),
                'f_validtim': (('', fore['VALIDTIME'])[fore['VALIDTIME'] is not None]).encode('utf-8'),
                'f_tau': fore['TAU'],
                'f_maxwind': fore['MAXWIND'],
                'f_gust': fore['GUST'],
                'f_tcspd': fore['TCSPD'],
                'f_mslp': fore['MSLP'],
                'f_tcdvlp': (('', fore['TCDVLP'])[fore['TCDVLP'] is not None]).encode('utf-8'),
                'f_dvlbl': (('', fore['DVLBL'])[fore['DVLBL'] is not None]).encode('utf-8'),
                'f_ssnum': fore['SSNUM'],
                'f_tcdir': fore['TCDIR'],
                'f_datelbl': (('', fore['DATELBL'])[fore['DATELBL'] is not None]).encode('utf-8'),
                'f_stormnum': fore['STORMNUM'],
                'f_fldatelb': (('', fore['FLDATELBL'])[fore['FLDATELBL'] is not None]).encode('utf-8'),
                'f_timezone': (('', fore['TIMEZONE'])[fore['TIMEZONE'] is not None]).encode('utf-8'),
                'f_stormsrc': (('', fore['STORMSRC'])[fore['STORMSRC'] is not None]).encode('utf-8'),
                'f_lat': fore['LAT'],
                'f_lon': fore['LON'],
                'stormid' : (('', pos['STORMID'])[pos['STORMID'] is not None]).encode('utf-8'),
                'appid' : (('', appid)[appid is not None]).encode('utf-8'),
                'eventid' : event['data']['id'].encode('utf-8')
                } 
            }
        
        if fore['ADVISNUM'] is not None and fore['ADVISNUM'].strip():
            featureData['f_advisnum'] = int(fore['ADVISNUM'])
    
        if 'rings' in buff['geometry']:
            featureData['geometry'] = {
                            'rings' : buff['geometry']['rings']
                            }
        elif 'curveRings' in buff['geometry']:
            featureData['geometry'] = {
                            'curveRings' : buff['geometry']['curveRings']
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