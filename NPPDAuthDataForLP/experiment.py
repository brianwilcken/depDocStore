import arcpy
from arcpy import env

env.workspace = r'E:\hurricane\NHC_Sample_Data.gdb'

fcList = arcpy.ListFeatureClasses()

FC = 'al112017_020_5day_pgn'

desc = arcpy.Describe(FC)