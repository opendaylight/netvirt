from model import Model


NAME = "id-manager"


def id_pools(store, ip=None, port=None, path=None):
    return IdPools(NAME, IdPools.CONTAINER, store, ip, port, path)


class IdPools(Model):
    CONTAINER = "id-pools"
    ID_POOL = "id-pool"

    def get_id_pools(self):
        return self.data[self.CONTAINER][self.ID_POOL]

    def get_id_pools_by_key(self, key="pool-name"):
        d = {}
        idpools = self.get_id_pools()
        for idpool in idpools:
            d[idpool[key]] = idpool
        return d
