# -*- coding: utf-8 -*-
"""
Created on Wed May 16 09:04:06 2018

@author: WILCBM
"""

import logging
import logging.config
import WildfireEventsTracker

logging.config.fileConfig('logging.conf')

logger = logging.getLogger('authDataLogger')

logger.info('Begin Processing Wildfire Events from NPPD')
tracker = WildfireEventsTracker.WildFireEventsTracker()
tracker.getAuthoritativeData()
logger.info('Finished Processing Wildfire Events from NPPD')