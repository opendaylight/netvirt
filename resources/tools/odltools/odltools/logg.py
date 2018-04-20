import logging


class Logger:
    def __init__(self):
        logger = logging.getLogger()
        formatter = logging.Formatter('%(asctime)s - %(levelname).3s - %(name)-20s - %(lineno)04d - %(message)s')
        ch = logging.StreamHandler()
        ch.setLevel(logging.INFO)
        ch.setFormatter(formatter)
        logger.addHandler(ch)
        fh = logging.FileHandler("/tmp/odltools.txt", "w")
        fh.setLevel(logging.DEBUG)
        fh.setFormatter(formatter)
        logger.addHandler(fh)
        logger.setLevel(min([ch.level, fh.level]))
