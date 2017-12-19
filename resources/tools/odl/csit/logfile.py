import logging
import os
import re
from subprocess import Popen


LOG = logging.getLogger(__name__)


class LogFile():
    TMP = "/tmp"
    LOGFILE = "log.html"

    def __init__(self, logpath, jobpath, job):
        if jobpath is None:
            jobpath = self.TMP
        self.jobpath = "{}/{}".format(jobpath, job)
        self.logpath = logpath

    def unzip_log_file0(self):
        Popen("gunzip -fk {}".format(self.logpath), shell=True).wait()

    def unzip_log_file1(self):
        Popen("gunzip -kc {} > {}".format(self.logpath, self.jobpath + "/log.html"), shell=True).wait()

    def unzip_log_file(self):
        Popen("gunzip -cfk {} > {}".format(self.logpath, self.jobpath + "/" + self.LOGFILE), shell=True).wait()

    def mkdir_job_path(self):
        try:
            os.makedirs(self.jobpath)
        except OSError:
            if not os.path.isdir(self.jobpath):
                raise

    def read_chunks(self, fp):
        while True:
            data = fp.read(64000)
            if not data:
                break
            yield data

    def parse_log(self, log):
        # logfile = "/tmp/log.s2.html"
        logfile = "/tmp/testjob/log.html"
        # re_st = re.compile(r"ROBOT MESSAGE: Starting test")
        re_st = re.compile(r"dump-flows")
        cnt = 0
        with open(logfile, 'rb') as fp:
            for chunk in self.read_chunks(fp):
                for m in re_st.finditer(chunk):
                    print('%02d-%02d: %s' % (m.start(), m.end(), m.group(0)))
                    cnt += 1
        print "total matches: {}".format(cnt)
