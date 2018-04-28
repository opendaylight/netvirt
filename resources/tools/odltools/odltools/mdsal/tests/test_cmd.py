import logging
import os
import shutil
import unittest

from odltools import logg
from odltools.mdsal import cmd
from odltools.mdsal.tests import Args


class TestCmd(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.DEBUG, logging.DEBUG)
        self.args = Args(path="/tmp/testmodels", pretty_print=True)

    def test_get_all_dumps(self):
        # Ensure odl is running at localhost:8181
        shutil.rmtree(self.args.path)
        cmd.get_all_dumps(self.args)

        # assert each model has been saved to a file
        for res in cmd.DSMAP.itervalues():
            store = res[cmd.DSM_DSTYPE]
            model_path = res[cmd.DSM_PATH]
            path_split = cmd.split_model_path(model_path)
            filename = cmd.make_filename(self.args.path, store, path_split.name, path_split.container)
            self.assertTrue(os.path.isfile(filename))


if __name__ == '__main__':
    unittest.main()
