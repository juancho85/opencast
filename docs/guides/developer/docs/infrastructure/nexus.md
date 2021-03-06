Opencast Nexus (Maven Repository)
=================================

The Maven Nexus server maintains a copy of all the Java dependencies used by Opencast.  The Opencast project has several
Nexus servers located around the world. For an up-to-date list of servers as well as their current status and your
automatic selection, have a look at the [Nexus GeoIP Redirect Service](http://nexus.opencast.org).


GeoIP Server Set-up
-------------------

The main entry point for retrieving artifacts is nexus.opencast.org, which runs a [GeoIP based Gateway service
](https://github.com/lkiesow/geoip-gateway) to probe which Nexus server is likely the best to use for that request and
then redirects the request accordingly.

The Nexus servers are configured to retrieve and cache artifacts from:

- Apache Snapshots
- Codehaus Snapshots
- Maven Central
- *Opencast Master*

*Opencast Master* is a special repository pointing to the [Opencast Nexus server hosted at the University of Osnabrück
](http://nexus.virtuos.uos.de). It does not only cache other repositories but also host Opencast internal artifacts.
Since all other repositories will automatically retrieve artifacts from this repository, uploading artifacts to this
repository will suffice to distribute them in the whole Nexus infrastructure.


Adding Libraries To The Repository
----------------------------------

1. Login as an administrator on the [Opencast Nexus Master](http://nexus.virtuos.uos.de)
2. Select repository
3. Select the artifact upload tab
4. Fill in the details and upload the file

*As result of the Nexus caching mechanism, it might take up to 48h until the artifacts are distributed to all Nexus
repository nodes.*


Setting-up Another Nexus Server
-------------------------------

Having a Nexus server run in your local network can significantly improve the speed artifacts are retrieved while
building Opencast.


### Set-up Using Docker

There is a preconfigured Docker image for a Nexus server set-up for Opencast. To run an Opencast Nexus using Docker,
follow these steps:

    docker pull opencast/opencast-nexus-oss
    docker run \
       --name opencast-nexus-oss \
       -p 8081:8081 \
      -v /place/for/nexus/data/on/host/machine:/var/lib/nexus-oss \
      opencast/opencast-nexus-oss

- The `-p` option will map the internal port of the Nexus server in Docker to the port on the host machine.
- The `-v` option will mount the folder `/place/for/nexus/data` from the Docker host machine to `/var/lib/nexus-oss`
  container. This will keep all your runtime data our of the container and you can then later upgrade to a new Nexus
  version by just getting a new container.

Make sure to log-in and change the password for the user `admin` as soon as your Nexus is running. To do that, head to
`http://localhost:8081/nexus` and log-in as user `admin` using the password `admin123`.


### Update Using Docker

If you followed the set-up guide above, the main configuration, along with all the Nexus data is stored on the host
machine. Hence we just need to get the latest Docker image and replace the Nexus container:

    docker rm -f opencast-nexus-oss
    docker pull opencast/opencast-nexus-oss
    docker run \
       --name opencast-nexus-oss \
       -p 8081:8081 \
      -v /place/for/nexus/data/on/host/machine:/var/lib/nexus-oss \
      opencast/opencast-nexus-oss


### Set-up Using RPMs

For Nexus-OSS, there is a [Fedora Copr repository available](https://copr.fedorainfracloud.org/coprs/lkiesow/nexus-oss/)
for CentOS/RHEL 7.x and the latest Fedora versions. You can use these to easily istall Nexus.

First, enable the repository. For CentOS/RHEL 7.x this would be:

    curl -o /etc/yum.repos.d/lkiesow-nexus-oss-epel-7.repo \
      https://copr.fedorainfracloud.org/coprs/lkiesow/nexus-oss/repo/epel-7/lkiesow-nexus-oss-epel-7.repo

Then istall Nexus-OSS:

    yum install nexus-oss

Download the [configuration file](nexus.xml) and place it in `/var/lib/nexus-oss/conf/`:

    mkdir -p /var/lib/nexus-oss/conf/
    cp nexus.xml /var/lib/nexus-oss/conf/nexus.xml

Finally, activate the service:

    systemctl start nexus-oss
    systemctl enable nexus-oss

Make sure to log-in and change the password for the user `admin` as soon as your Nexus is running. To do that, head to
`http://localhost:8081/nexus` and log-in as user `admin` using the password `admin123`.


Use a Specific Opencast Nexus
-----------------------------

If you did set-up a local Nexus repository you can add a custom Maven configuration to overwrite the global nexus server
settings. To do that, create a user specific Maven settings file in `~/.m2/settings.xml` like this:

    <settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
      xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
      xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0
      http://maven.apache.org/xsd/settings-1.0.0.xsd">
      <profiles>
        <profile>
          <id>set-opencast-nexus</id>
          <properties>
            <opencast.nexus.url>http://nexus.example.com</opencast.nexus.url>
          </properties>
        </profile>
      </profiles>
      <activeProfiles>
        <activeProfile>set-opencast-nexus</activeProfile>
      </activeProfiles>
    </settings>

This will let Maven overwrite the `opencast.nexus.url` property used by Opencast to determine the Nexus server during
the build process.
