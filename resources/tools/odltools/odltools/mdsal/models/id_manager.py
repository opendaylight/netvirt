from odltools.mdsal.models.model import Model


NAME = "id-manager"


def id_pools(store, args):
    return IdPools(NAME, IdPools.CONTAINER, store, args)


class IdPools(Model):
    CONTAINER = "id-pools"
    ID_POOL = "id-pool"

    def get_id_pools(self):
        return self.data[self.CONTAINER][self.ID_POOL]

    def get_id_pools_by_key(self, key="pool-name"):
        d = {}
        idpools = self.get_id_pools()
        if idpools is None:
            return None
        for idpool in idpools:
            d[idpool[key]] = idpool
        return d
