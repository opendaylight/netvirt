import unittest

from odltools import logg
from odltools.netvirt import show
from odltools.netvirt.tests import Args


class TestShow(unittest.TestCase):
    # TODO: capture stdout and check for list of tables.

    def setUp(self):
        logg.Logger()
        self.args = Args(path="../../tests/resources")

    def test_show_elan_instances(self):
        show.show_elan_instances(self.args)

    def test_show_groups(self):
        show.show_groups(self.args)

    def test_show_tables(self):
        show.show_tables(self.args)

if __name__ == '__main__':
    unittest.main()
