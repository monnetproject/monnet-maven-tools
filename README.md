Monnet Maven Tools
==================

osgirun
-------

Starts an OSGi platform (by default Felix), using all JARs that are on the Maven build, test and runtime path. If these
JARs do not have OSGi metadata, this is added by default to the JARs before they are deployed.

### Usage:

Add the following to your `pom.xml`:

    <pluginRepositories>
      <pluginRepository>
        <id>monnet01</id>
        <name>Monnet plug-in repository</name>
        <url>http://monnet01.sindice.net/mvn</url>
      </pluginRepository>
    </pluginRepositories>
	
    <build>
      <plugins>
        <plugin>
          <groupId>eu.monnetproject</groupId>
          <artifactId>osgirun</artifactId>
          <version>1.12.2</version>
        </plugin>
      </plugins>
    </build>

Then an OSGi enviroment can be started simply with the Maven command

    mvn osgirun:run

A different framework can be chosen as follows

    mvn osgirun:run -Dosgirun.fwGroupId=org.apache.felix  \
        -Dosgirun.fwArtifactId=org.apache.felix.framework \
        -Dosgirun.fwVersion=4.2.0

If you do not wish to start Felix's TUI then also include the flag

   mvn osgirun:run -Dosgirun.felixBundles=false

Finally, if you have bundles that are part of the build but not compatible with OSGi you may exclude them as follows

   mvn osgirun:run -Dosgirun.excludeBundles=artifactId1,artifactId2

### Integration testing

The osgirun plugin may be used in combination with the [BeInformed OSGi test framework](http://github.com/beinformed/osgitest) by including the following
in your pom.xml

   <pluginRepositories>
      <pluginRepository>
        <id>monnet01</id>
        <name>Monnet plug-in repository</name>
        <url>http://monnet01.sindice.net/mvn</url>
      </pluginRepository>
    </pluginRepositories>
	
    <build>
      <plugins>
        <plugin>
          <groupId>eu.monnetproject</groupId>
          <artifactId>osgirun</artifactId>
          <version>1.12.2</version>
          <executions>
            <execution>
              <goals>
                <goal>run</goal>
              </goals>
              <phase>integration-test</phase>
              <configuration>
                <integrationTest>true</integrationTest>
              </configuration>
            </execution>
          </executions>
        </plugin>
      </plugins>
    </build>


bndannotation
-------------

BND annotation tool takes annotations in the [BND format](http://www.aqute.biz/Bnd/Bnd) and adds appropriate meta-data
to make them work as SCR components in an OSGi format.

This component is only used for legacy reasons, in preference the [Felix bundle plugin](http://felix.apache.org/site/apache-felix-maven-bundle-plugin-bnd.html) should be used

