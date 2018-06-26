# -*- coding: utf-8 -*-
"""
Created on Wed May 16 09:04:06 2018

@author: WILCBM
"""

import logging
import logging.config
import WildfireEventsTracker
import NWSEventsTracker
import HurricaneEventsTracker

logging.config.fileConfig('logging.conf')

logger = logging.getLogger('authDataLogger')

#logger.info('Begin Processing Wildfire Events from NPPD')
#wildfireEventsTracker = WildfireEventsTracker.WildFireEventsTracker()
#wildfireEventsTracker.getAuthoritativeData()
#logger.info('Finished Processing Wildfire Events from NPPD')

#logger.info('Begin Processing NWS Events from NPPD')
#nwsEventsTracker = NWSEventsTracker.NWSEventsTracker()
#nwsEventsTracker.getAuthoritativeData()
#logger.info('Finished Processing NWS Events from NPPD')

logger.info('Begin Processing Hurricane Events from NPPD')
hurricaneEventsTracker = HurricaneEventsTracker.HurricaneEventsTracker()
hurricaneEventsTracker.getAuthoritativeData()
logger.info('Finished Processing Hurricane Events from NPPD')