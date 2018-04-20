import unittest
from odltools import odltools
from csit import robotfiles


class TestOdltools(unittest.TestCase):
    DATAPATH = "/tmp/output_01_l2.xml.gz"
    OUTPATH = "/tmp/robotjob"

    def test_parser_empty(self):
        parser = odltools.create_parser()
        with self.assertRaises(SystemExit) as cm:
            parser.parse_args([])
        self.assertEqual(cm.exception.code, 2)

    def test_parser_help(self):
        parser = odltools.create_parser()
        with self.assertRaises(SystemExit) as cm:
            parser.parse_args(['-h'])
        self.assertEqual(cm.exception.code, 0)

    def test_robotfiles_run(self):
        parser = odltools.create_parser()
        args = parser.parse_args(['csit', self.DATAPATH, self.OUTPATH, '-g'])
        robotfiles.run(args)

    def test_csit(self):
        parser = odltools.create_parser()
        args = parser.parse_args(['csit', self.DATAPATH, self.OUTPATH, '-g', '-d'])
        robotfiles.run(args)

if __name__ == '__main__':
    unittest.main()
