from odltools.mdsal.models.model import Model


MODULE = "id-manager"


def id_pools(store, args):
    return IdPools(MODULE, store, args)


class IdPools(Model):
    CONTAINER = "id-pools"
    CLIST = "id-pool"
    CLIST_KEY = "pool-name"
