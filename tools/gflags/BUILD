# Extracted version of https://github.com/google/python-gflags/archive/python-gflags-2.0.zip

py_library(
    name = "gflags_lib",
    srcs = [
        "gflags.py",
        "gflags_validators.py",
    ],
    visibility = ["//visibility:public"],
)


# The original Bazel Eclipse plugin loaded this via a WORKSPACE repository rule.
# We are trying to minimize build-time callouts to external internet hosts, and it is a 
# simple lib, so we interned it instead.

#new_http_archive(
#    name = "com_google_python_gflags",
#    url = "https://github.com/google/python-gflags/archive/python-gflags-2.0.zip",
#    strip_prefix = "python-gflags-python-gflags-2.0",
#    build_file_content = """
#py_library(
#    name = "gflags",
#    srcs = [
#        "gflags.py",
#        "gflags_validators.py",
#    ],
#    visibility = ["//visibility:public"],
#)
#""",
#    sha256 = "344990e63d49b9b7a829aec37d5981d558fea12879f673ee7d25d2a109eb30ce",
#)
