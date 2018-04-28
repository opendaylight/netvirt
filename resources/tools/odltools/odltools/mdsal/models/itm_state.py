from odltools.mdsal.models.model import Model


NAME = "itm-state"


def dpn_endpoints(store, args):
    return DpnEndpoints(NAME, DpnEndpoints.CONTAINER, store, args)


def interfaces(store, args):
    return DpnTepsState(NAME, DpnTepsState.CONTAINER, store, args)


def tunnels_state(store, args):
    return TunnelsState(NAME, TunnelsState.CONTAINER, store, args)


class DpnEndpoints(Model):
    CONTAINER = "dpn-endpoints"
    DPN_TEPS_INFO = "DPN-TEPs-info"
    DPN_ID = "DPN-ID"
    TUNNEL_END_POINTS = "tunnel-end-points"
    IP_ADDRESS = "ip-address"

    def get_dpn_teps_infos(self):
        return self.data[self.CONTAINER][self.DPN_TEPS_INFO]

    def get_dpn_teps_info(self, dpn_id):
        dpn_teps_infos = self.get_dpn_teps_infos()
        for dpn_teps_info in dpn_teps_infos:
            if dpn_teps_info[self.DPN_ID] == dpn_id:
                return dpn_teps_info

    def get_tunnel_endpoints(self, dpn_id):
        dpn_teps_infos = self.get_dpn_teps_infos()
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
    DPN_TEPS = "dpns-teps"

    def get_dpn_teps(self):
        return self.data[self.CONTAINER][self.DPN_TEPS]

    def get_tuninterfaces_by_name(self):
        d = {}
        tunifaces = self.get_dpn_teps()
        if tunifaces is None:
            return None
        for sourcedpn in tunifaces:
            for remotedpn in sourcedpn['remote-dpns']:
                d[remotedpn['tunnel-name']] = remotedpn
        return d


class TunnelsState(Model):
    CONTAINER = "tunnels_state"
    STATE_TUNNEL_LIST = "state-tunnel-list"

    def get_state_tunnel_list(self):
        return self.data[self.CONTAINER][self.STATE_TUNNEL_LIST]

    def get_tunnels_by_key(self, key="tunnel-interface-name"):
        d = {}
        tunnels = self.get_state_tunnel_list()
        if tunnels is None:
            return None
        for tunnel in tunnels:
            d[tunnel[key]] = tunnel
        return d
