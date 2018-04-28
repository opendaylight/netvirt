from odltools.mdsal.models.model import Model


NAME = "mip"


def mac(store, args):
    return Mac(NAME, Mac.CONTAINER, store, args)


class Mac(Model):
    CONTAINER = "mac"
    ENTRY = "entry"

    def get_entries(self):
        return self.data[self.CONTAINER][self.ENTRY]

    def get_entries_by_key(self, key="name"):
        d = {}
        entries = self.get_entries()
        if entries is None:
            return None
        for entry in entries:
            entry['mac'] = entry['mac'].lower()
        d[entry.get('mac')][entry.get('network-id')] = entry
        return d
