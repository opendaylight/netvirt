from model import Model


NAME = "odl-fib"


def fib_entries(store, ip=None, port=None, path=None):
    return FibEntries(NAME, FibEntries.CONTAINER, store, ip, port, path)


class FibEntries(Model):
    CONTAINER = "fibEntries"
    VRFTABLES = "vrfTables"
    VRFENTRY = "vrfEntry"
    ROUTEDISTINGUISHER = "routeDistinguisher"
    RD = "rd"

    def get_vrf_tables(self):
        return self.data[self.CONTAINER][self.VRFTABLES]

    def get_vrf_entries_by_key(self, key="label"):
        d = {}
        vrf_tables = self.get_vrf_tables()
        for vrf_table in vrf_tables:
            for vrf_entry in vrf_table.get(self.VRFENTRY, []):
                if vrf_entry.get('label'):
                    vrf_entry[self.RD] = vrf_table[self.ROUTEDISTINGUISHER]
                    d[vrf_entry[key]] = vrf_entry
        return d
