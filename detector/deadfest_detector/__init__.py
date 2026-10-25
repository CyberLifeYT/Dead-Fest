"""Dead-Fest Xbox Death Detector: finds death screens in an Xbox video feed and keeps the evidence."""

import os

__version__ = "1.0.0"

# OpenCV prints a warning for every capture index it probes; errors are reported by the detector itself.
os.environ.setdefault("OPENCV_LOG_LEVEL", "ERROR")
