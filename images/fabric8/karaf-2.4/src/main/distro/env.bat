@echo off

rem  Copyright 2005-2015 Red Hat, Inc.
rem
rem  Red Hat licenses this file to you under the Apache License, version
rem  2.0 (the "License"); you may not use this file except in compliance
rem  with the License.  You may obtain a copy of the License at
rem
rem     http://www.apache.org/licenses/LICENSE-2.0
rem
rem  Unless required by applicable law or agreed to in writing, software
rem  distributed under the License is distributed on an "AS IS" BASIS,
rem  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
rem  implied.  See the License for the specific language governing
rem  permissions and limitations under the License.
rem

rem defines the default environment settings

set PID_FILE=%APP_BASE%\process.pid

if not defined JOLOKIA_PORT set JOLOKIA_PORT=8778
if not defined KARAF_USERNAME set KARAF_USERNAME=admin
if not defined KARAF_PASSWORD set KARAF_PASSWORD=admin

set KARAF_OPTS=-Dkaraf.shutdown.pid.file=process.pid -javaagent:jolokia-agent.jar=host=0.0.0.0,port=%JOLOKIA_PORT%,authMode=jaas,realm=karaf,user=%KARAF_USERNAME%,password=%KARAF_PASSWORD

rem export the ports as system properties
set KARAF_OPTS=%KARAF_OPTS% -Dhttp.port=%HTTP_PORT% -Drmi.registry.port=%RMI_REGISTRY_PORT% -Drmi.server.port=%RMI_SERVER_PORT% -Dssh.port=%SSH_PORT%
