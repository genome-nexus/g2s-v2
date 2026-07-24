@echo off
rem blastp-docker.cmd
rem
rem Local-machine shim so pdb-alignment-web's "search by protein sequence"
rem endpoints can run blastp via the pre-pulled ncbi/blast Docker image instead
rem of requiring a native BLAST+ install on PATH.
rem
rem CommandProcessUtil.java invokes this file exactly as it would invoke a real
rem `blastp` executable (see blastp= in application-local.properties), passing
rem the usual -db/-query/-out flags with absolute Windows paths under g2s/
rem (workspace=/uploaddir= in the same properties file). This rewrites those
rem paths to the /g2s mount point below and runs blastp inside the container.

set "ARGS=%*"
set "ARGS=%ARGS:C:/CursorProjects/cbioportal260530/g2s=/g2s%"
set "ARGS=%ARGS:c:/CursorProjects/cbioportal260530/g2s=/g2s%"

docker run --rm -v C:/CursorProjects/cbioportal260530/g2s:/g2s ncbi/blast:2.16.0 blastp %ARGS%
exit /b %ERRORLEVEL%
