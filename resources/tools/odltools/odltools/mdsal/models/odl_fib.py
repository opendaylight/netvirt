from odltools.mdsal.models.model import Model


MODULE = "odl-fib"


def fib_entries(store, args):
    return FibEntries(MODULE, store, args)


class FibEntries(Model):
    CONTAINER = "fibEntries"
    CLIST = "vrfTables"
    CLIST_KEY = "routeDistinguisher"
    VRFENTRY = "vrfEntry"
    ROUTEDISTINGUISHER = "routeDistinguisher"
    ROUTEPATHS = "route-paths"
    RD = "rd"

    def get_vrf_entries_by_key(self, key="label"):
        d = {}
        vrf_tables = self.get_clist()
        for vrf_table in vrf_tables:
            for vrf_entry in vrf_table.get(self.VRFENTRY, []):
                for route_paths in vrf_entry.get(FibEntries.ROUTEPATHS, {}):
                    if route_paths.get(key):
                        vrf_entry[self.RD] = vrf_table[self.ROUTEDISTINGUISHER]
                        d[route_paths.get(key)] = vrf_entry
        return d
