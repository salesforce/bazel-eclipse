#
# Verbose build logging
#
# To get more output during a build, set this flag to True
#

_VERBOSE=False

def verbose(message):
   if _VERBOSE: print(message)
