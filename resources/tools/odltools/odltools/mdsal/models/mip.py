from odltools.mdsal.model import Model


NAME = "mip"


def mac(store, ip=None, port=None, path=None):
    return Mac(NAME, Mac.CONTAINER, store, ip, port, path)


class Mac(Model):
    CONTAINER = "mac"
    ENTRY = "entry"

    def get_entries(self):
        return self.data[self.CONTAINER][self.ENTRY]

    def get_entries_by_key(self, key="name"):
        d = {}
        entries = self.get_entries()
        for entry in entries:
            entry['mac'] = entry['mac'].lower()
        d[entry.get('mac')][entry.get('network-id')] = entry
        return d
