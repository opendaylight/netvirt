from odltools.mdsal.models.model import Model


MODULE = "odl-interface-meta"


def if_indexes_interface_map(store, args):
    return IfIndexesInterfaceMap(MODULE, store, args)


class IfIndexesInterfaceMap(Model):
    CONTAINER = "if-indexes-interface-map"
    CLIST = "if-index-interface"
    CLIST_KEY = "if-index"
