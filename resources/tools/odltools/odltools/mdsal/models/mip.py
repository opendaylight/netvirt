from odltools.mdsal.models.model import Model


MODULE = "mip"


def mac(store, args):
    return Mac(MODULE, store, args)


class Mac(Model):
    CONTAINER = "mac"
    CLIST = "entry"
    CLIST_KEY = "name"
