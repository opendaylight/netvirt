from odltools.mdsal.model import Model


NAME = "odl-l3vpn"


def vpn_id_to_vpn_instance(store, ip=None, port=None, path=None):
    return VpnIdToVpnInstance(NAME, VpnIdToVpnInstance.CONTAINER, store, ip, port, path)


def vpn_instance_to_vpn_id(store, ip=None, port=None, path=None):
    return VpnInstanceToVpnId(NAME, VpnInstanceToVpnId.CONTAINER, store, ip, port, path)


class VpnIdToVpnInstance(Model):
    CONTAINER = "vpn-id-to-vpn-instance"
    VPN_IDS = "vpn-ids"

    def get_vpn_ids(self):
        return self.data[self.CONTAINER][self.VPN_IDS]

    def get_vpn_ids_by_key(self, key="vpn-id"):
        d = {}
        vpnids = self.get_vpn_ids()
        for vpnid in vpnids:
            d[vpnid[key]] = vpnid
        return d


class VpnInstanceToVpnId(Model):
    CONTAINER = "vpn-instance-to-vpn-id"
    VPN_INSTANCE = "vpn-instance"

    def get_vpn_instances(self):
        return self.data[self.CONTAINER][self.VPN_INSTANCE]

    def get_vpn_instances_by_key(self, key="vpn-id"):
        d = {}
        instances = self.get_vpn_instances()
        for instance in instances:
            d[instance[key]] = instance
        return d
