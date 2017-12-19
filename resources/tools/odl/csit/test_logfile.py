import os
import unittest
from logfile import LogFile


class TestLogFile(unittest.TestCase):
    LOGPATH = "/tmp/log.html.gz"
    JOBPATH = "/tmp"
    JOB = "testjob"

    def setUp(self):
        self.logfile = LogFile(self.LOGPATH, self.JOBPATH, self.JOB)

    def test_mkdir_log_path(self):
        self.logfile.mkdir_job_path()
        self.assertTrue(os.path.isdir(self.logfile.jobpath))

    def test_unzip_log_file(self):
        self.logfile.mkdir_job_path()
        self.logfile.unzip_log_file()
        fname = "{}/log.html".format(self.logfile.jobpath)
        self.assertTrue(os.path.isfile(fname))

    def test_parse_log(self):
        self.logfile.parse_log(None)

if __name__ == '__main__':
    unittest.main()
