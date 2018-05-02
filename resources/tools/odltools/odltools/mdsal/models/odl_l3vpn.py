from odltools.mdsal.models.model import Model


MODULE = "odl-l3vpn"


def vpn_id_to_vpn_instance(store, args):
    return VpnIdToVpnInstance(MODULE, store, args)


def vpn_instance_to_vpn_id(store, args):
    return VpnInstanceToVpnId(MODULE, store, args)


class VpnIdToVpnInstance(Model):
    CONTAINER = "vpn-id-to-vpn-instance"
    CLIST = "vpn-ids"
    CLIST_KEY = "vpn-id"


class VpnInstanceToVpnId(Model):
    CONTAINER = "vpn-instance-to-vpn-id"
    CLIST = "vpn-instance"
    CLIST_KEY = "vpn-id"
