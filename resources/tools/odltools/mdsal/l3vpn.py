from model import Model


NAME = "l3vpn"


def vpn_instance_to_vpn_id(store, ip=None, port=None, path=None):
    return VpnInterfaces(NAME, VpnInterfaces.CONTAINER, store, ip, port, path)


class VpnInterfaces(Model):
    CONTAINER = "vpn-interfaces"
    VPN_INTERFACE = "vpn-interface"

    def get_vpn_interfaces(self):
        return self.data[self.CONTAINER][self.VPN_INTERFACE]

    def get_vpn_ids_by_key(self, key="name"):
        d = {}
        ifaces = self.get_vpn_interfaces()
        for iface in ifaces:
            d[iface[key]] = iface
        return d
