from odltools.mdsal.models.model import Model


MODULE = "itm-state"


def dpn_endpoints(store, args):
    return DpnEndpoints(MODULE, store, args)


def interfaces(store, args):
    return DpnTepsState(MODULE, store, args)


def tunnels_state(store, args):
    return TunnelsState(MODULE, store, args)


class DpnEndpoints(Model):
    CONTAINER = "dpn-endpoints"
    CLIST = "DPN-TEPs-info"
    CLIST_KEY = ""
    DPN_ID = "DPN-ID"
    TUNNEL_END_POINTS = "tunnel-end-points"
    IP_ADDRESS = "ip-address"

    def get_dpn_teps_info(self, dpn_id):
        dpn_teps_infos = self.get_clist()
        for dpn_teps_info in dpn_teps_infos:
            if dpn_teps_info[self.DPN_ID] == dpn_id:
                return dpn_teps_info

    def get_tunnel_endpoints(self, dpn_id):
        dpn_teps_infos = self.get_clist()
        for dpn_teps_info in dpn_teps_infos:
            if dpn_teps_info[self.DPN_ID] == dpn_id:
                return dpn_teps_info[self.TUNNEL_END_POINTS]

    def get_dpn_ids(self):
        return self.get_kv(DpnEndpoints.DPN_ID, self.data, values=[])

    def get_ip_address(self, dpn_id):
        tunnel_endpoints = self.get_tunnel_endpoints(dpn_id)
        return tunnel_endpoints[0][self.IP_ADDRESS]


class DpnTepsState(Model):
    CONTAINER = "dpn-teps-state"
    CLIST = "dpns-teps"

    def get_tuninterfaces_by_name(self):
        d = {}
        tunifaces = self.get_clist()
        for sourcedpn in tunifaces:
            for remotedpn in sourcedpn['remote-dpns']:
                d[remotedpn['tunnel-name']] = remotedpn
        return d


class TunnelsState(Model):
    CONTAINER = "tunnels_state"
    CLIST = "state-tunnel-list"
    CLIST_KEY = "tunnel-interface-name"
