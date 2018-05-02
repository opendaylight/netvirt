from odltools.mdsal.models.model import Model


MODULE = "l3vpn"


def vpn_instance_to_vpn_id(store, args):
    return VpnInterfaces(MODULE, store, args)


class VpnInterfaces(Model):
    CONTAINER = "vpn-interfaces"
    CLIST = "vpn-interface"
    CLIST_KEY = "name"
