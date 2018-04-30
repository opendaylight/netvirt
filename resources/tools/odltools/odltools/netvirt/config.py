from odltools.mdsal.models.models import Models


gmodels = None


def get_models(args, models):
    global gmodels
    gmodels = Models()
    gmodels.get_models(args, models)


