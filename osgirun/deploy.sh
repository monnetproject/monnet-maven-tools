#!/bin/bash

scp generated/eu.monnetproject.framework.osgirun.jar johmcc@monnet01.sindice.net:/var/www/johmcc/mvn/eu/monnetproject/osgirun/1.12.1/
scp ~/.m2/repository/eu/monnetproject/osgirun/1.12.1/*.pom johmcc@monnet01.sindice.net:/var/www/johmcc/mvn/eu/monnetproject/osgirun/1.12.1/

