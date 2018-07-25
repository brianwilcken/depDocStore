import sys
import getopt
import logging
import logging.config
import WildfireEventsTracker
import NWSEventsTracker
import HurricaneEventsTracker

def main(argv):
    
    logging.config.fileConfig('logging.conf')
    
    logger = logging.getLogger('authDataLogger')
    
    try:
        opts, args = getopt.getopt(argv, 'e:', ['env='])
    except getopt.GetoptError:
        print 'GetAuthoritativeData.py -e <environment>'
        sys.exit(2)
        
    portalInfo = {}

    if len(opts) > 0:
        for opt, arg in opts:
            if opt in ('-e', 'env'):
                portalInfo['env'] = arg
                if arg == 'D':
                    logger.info('Development mode')
                    eventsServiceUrl = 'http://localhost:8080/eventNLP/api/events'
                    portalInfo['generateTokenUrl'] = 'https://cloudgis.k2geomatics.io/portal/sharing/rest/generateToken'
                    portalInfo['serverTokenUrl'] = 'https://cloudgis.k2geomatics.io/portal/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Fcloudgis.k2geomatics.io%2Fserver%2Flogin%2F..%2Frest%2Fservices&referer=cloudgis.k2geomatics.io&f=json&token='
                    portalInfo['baseUrl'] = 'https://cloudgis.k2geomatics.io/server/rest/services/Hosted'
                    portalInfo['tokenCred'] = 'username=NiccAppSys&password=TrnWK9*!CbR&ip=&referer=&client=requestip&expiration=60&f=pjson'
                    portalInfo['useNegotiateAuth'] = False
                elif arg == 'S':
                    logger.info('Staging mode')
                    eventsServiceUrl = 'http://s-nicc-web01:8080/eventNLP/api/events'
                    portalInfo['generateTokenUrl'] = 'https://webadaptorstag1.lens.iacc.dis.anl.gov/arcgis/sharing/rest/generateToken'
                    portalInfo['serverTokenUrl'] = 'https://webadaptorstag1.lens.iacc.dis.anl.gov/arcgis/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Fwebadaptorstag1.lens.iacc.dis.anl.gov%2Farcgis%2Flogin%2F..%2Frest%2Fservices&referer=webadaptorstag1.lens.iacc.dis.anl.gov&f=json&token='
                    portalInfo['baseUrl'] = 'https://webadaptorstag1.lens.iacc.dis.anl.gov/arcgis/rest/services/Hosted'
                    portalInfo['tokenCred'] = 'username=svcArcGis&domain=LENS&password=INLsvc!15&ip=&referer=&client=requestip&expiration=60&f=pjson'
                    portalInfo['useNegotiateAuth'] = True
                elif arg == 'P':
                    logger.info('Production mode')
                    eventsServiceUrl = 'http://p-nicc-web01:8080/eventNLP/api/events'
                    portalInfo['generateTokenUrl'] = 'https://webadaptor1.lens.iacc.dis.anl.gov/arcgis/sharing/rest/generateToken'
                    portalInfo['serverTokenUrl'] = 'https://webadaptor1.lens.iacc.dis.anl.gov/arcgis/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Fwebadaptor1.lens.iacc.dis.anl.gov%2Farcgis%2Flogin%2F..%2Frest%2Fservices&referer=webadaptor1.lens.iacc.dis.anl.gov&f=json&token='
                    portalInfo['baseUrl'] = 'https://webadaptor1.lens.iacc.dis.anl.gov/arcgis/rest/services/Hosted'
                    portalInfo['tokenCred'] = 'username=svcArcGis&domain=LENS&password=INLsvc!15&ip=&referer=&client=requestip&expiration=60&f=pjson'
                    portalInfo['useNegotiateAuth'] = True
    else:
        logger.info('Local Development mode')
        eventsServiceUrl = 'http://localhost:8080/eventNLP/api/events'
        portalInfo['generateTokenUrl'] = 'https://cloudgis.k2geomatics.io/portal/sharing/rest/generateToken'
        portalInfo['serverTokenUrl'] = 'https://cloudgis.k2geomatics.io/portal/sharing/generateToken?request=getToken&serverUrl=https%3A%2F%2Fcloudgis.k2geomatics.io%2Fserver%2Flogin%2F..%2Frest%2Fservices&referer=cloudgis.k2geomatics.io&f=json&token='
        portalInfo['baseUrl'] = 'https://cloudgis.k2geomatics.io/server/rest/services/Hosted'
        portalInfo['tokenCred'] = 'username=NiccAppSys&password=TrnWK9*!CbR&ip=&referer=&client=requestip&expiration=60&f=pjson'
        portalInfo['useNegotiateAuth'] = False
    
    if 'baseUrl' not in portalInfo:
        print 'GetAuthoritativeData.py -e <environment>'
        sys.exit(2)
    
    logger.info('Begin Processing Wildfire Events from NPPD')
    wildfireEventsTracker = WildfireEventsTracker.WildFireEventsTracker(eventsServiceUrl, portalInfo)
    wildfireEventsTracker.getAuthoritativeData()
    logger.info('Finished Processing Wildfire Events from NPPD')
    
#    logger.info('Begin Processing NWS Events from NPPD')
#    nwsEventsTracker = NWSEventsTracker.NWSEventsTracker(eventsServiceUrl, portalInfo)
#    nwsEventsTracker.getAuthoritativeData()
#    logger.info('Finished Processing NWS Events from NPPD')
#    
    logger.info('Begin Processing Hurricane Events from NPPD')
    hurricaneEventsTracker = HurricaneEventsTracker.HurricaneEventsTracker(eventsServiceUrl, portalInfo)
    hurricaneEventsTracker.getAuthoritativeData()
    #hurricaneEventsTracker.getTestData('daniel')
    logger.info('Finished Processing Hurricane Events from NPPD')
    
if __name__ == "__main__":
   main(sys.argv[1:])