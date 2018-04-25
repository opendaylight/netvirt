import logging
import os
import shutil
import unittest
from mdsal import models
from odltools import logg


class TestModels(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.DEBUG, logging.DEBUG)
        self.path = "/tmp/testmodels"
        self.ip = "127.0.0.1"
        self.port = "8181"
        self.user = "admin"
        self.pw = "admin"

    def test_get_all_dumps(self):
        shutil.rmtree(self.path)
        models.get_all_dumps(self.path, self.ip, self.port, self.user, self.pw, True)

        # assert each model has been saved to a file
        for res in models.DSMAP.itervalues():
            store = res[models.DSM_DSTYPE]
            model_path = res[models.DSM_PATH]
            path_split = models.split_model_path(model_path)
            filename = models.make_filename(self.path, store, path_split.name, path_split.container)
            self.assertTrue(os.path.isfile(filename))


if __name__ == '__main__':
    unittest.main()
