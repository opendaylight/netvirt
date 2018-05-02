import logging
import unittest

from odltools import logg
from odltools.netvirt import show
from odltools.netvirt import tests


class TestShow(unittest.TestCase):
    # TODO: capture stdout and check for list of tables.

    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        self.args = tests.Args(path=tests.get_resources_path())

    def test_show_elan_instances(self):
        show.show_elan_instances(self.args)

    def test_show_groups(self):
        show.show_groups(self.args)

    def test_show_flows_all(self):
        self.args.flowtype = "all"
        self.args.pretty_print = True
        show.show_flows(self.args)

    def test_show_stale_bindings(self):
        show.show_stale_bindings(self.args)

    def test_show_tables(self):
        show.show_tables(self.args)


if __name__ == '__main__':
    unittest.main()
