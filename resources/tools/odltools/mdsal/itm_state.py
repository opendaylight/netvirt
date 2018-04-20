from models import Model


NAME = "itm-state"


class DpnEndpoints(Model):
    CONTAINER = "dpn-endpoints"
    DPN_TEPS_INFO = "DPN-TEPs-info"
    DPN_ID = "DPN-ID"
    TUNNEL_END_POINTS = "tunnel-end-points"
    IP_ADDRESS = "ip-address"

    # not currently used, backup method to get_kv
    def item_generator(self, json_input, lookup_key):
        if isinstance(json_input, dict):
            for k, v in json_input.iteritems():
                if k == lookup_key:
                    yield v
                else:
                    for child_val in self.item_generator(v, lookup_key):
                        yield child_val
        elif isinstance(json_input, list):
            for item in json_input:
                for item_val in self.item_generator(item, lookup_key):
                    yield item_val

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


def dpn_endpoints(store, ip, port):
    return DpnEndpoints(NAME, DpnEndpoints.CONTAINER, store, ip, port)
