from odltools.mdsal.models.model import Model


MODULE = "ietf-interfaces"


def interfaces(store, args):
    return Interfaces(MODULE, store, args)


def interfaces_state(store, args):
    return InterfacesState(MODULE, store, args)


class Interfaces(Model):
    CONTAINER = "interfaces"
    CLIST = "interface"
    CLIST_KEY = "name"


class InterfacesState(Model):
    CONTAINER = "interfaces-state"
    CLIST = "interface"
    CLIST_KEY = "name"
