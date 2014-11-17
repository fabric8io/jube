#!/bin/sh
#
#  Copyright 2005-2014 Red Hat, Inc.
#
#  Red Hat licenses this file to you under the Apache License, version
#  2.0 (the "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
#  implied.  See the License for the specific language governing
#  permissions and limitations under the License.
#


if [ -d "$APP_BASE/maven/repository" ]; then
  echo "Copying offline repo"
  cp -rf $APP_BASE/maven/repository/* $APP_BASE/system/
fi

if [ -d "$APP_BASE/maven/kars" ]; then
  ln -s $APP_BASE/maven/kars/* $i $APP_BASE/deploy/
fi
