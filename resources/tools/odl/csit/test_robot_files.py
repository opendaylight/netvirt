import os
import unittest
from robotfiles import RobotFiles


# Requirements
# - output.xml.gz in /tmp
# - running tests will create job dir, unzip, parse and format output

class TestRobotFiles(unittest.TestCase):
    DATAPATH = "/tmp/output_01_l2.xml.gz"
    JOBPATH = "/tmp"
    JOBTAG = "testjob435"

    def test_mkdir_job_path(self):
        self.robotfile = RobotFiles(self.DATAPATH, self.JOBPATH, self.JOBTAG)
        self.robotfile.mkdir_job_path()
        self.assertTrue(os.path.isdir(self.robotfile.outdir))

    def test_gunzip_xml_data_file(self):
        self.robotfile = RobotFiles(self.DATAPATH, self.JOBPATH, self.JOBTAG)
        self.robotfile.mkdir_job_path()
        self.robotfile.gunzip_output_file()
        self.assertTrue(os.path.isfile(self.robotfile.datafilepath))

    def test_parse_xml_data_file(self):
        self.robotfile = RobotFiles("/tmp/testjob435/output_01_l2.xml", self.JOBPATH, self.JOBTAG)
        self.robotfile.print_config()
        self.robotfile.parse_xml_data_file()

        print "tests: {}".format(len(self.robotfile.pdata))
        # test_id = "s1-s1-s4-t28"
        test_id = "s1-t1"
        if test_id not in self.robotfile.pdata:
            self.fail("wrong test_id")
        pdata = self.robotfile.pdata[test_id]
        print "\n{} test id = {} - {}".format(1, test_id, pdata['name'])
        if 1:
            for nindex, (node, ndata) in enumerate(pdata['nodes'].items()):
                print "{}: node = {}".format(nindex, node)
                for cindex, (command, cdata) in enumerate(ndata.items()):
                    print "{}: command = {}\n{}".format(cindex, command, cdata)
        if 0:
            for mindex, (model, mdata) in enumerate(sorted(pdata['models'].items())):
                print "{}: model = {} - {}".format(mindex, model, mdata)

        self.robotfile.mkdir_job_path()
        self.robotfile.write_pdata()
        # self.robotfile.write_debug_pdata()

if __name__ == '__main__':
    unittest.main()
