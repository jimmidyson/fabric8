## Run OpenShift V3 using Docker


There are two common approaches to running OpenShift with [Docker](https://docs.docker.com/) and the prerequisites are different depending on how you install and run Docker.

### Prerequisites
Whether you can run and install Docker natively changes the steps you need to follow.  Please follow the correct steps for you..

  * Internet connection to download the start script and pull docker images
  * Suitable disk space for your desired usage.  Docker images can be quite large and 20G will disappear quickly, it's recommended to keep an eye on your available disk space.  There are some useful [clean up scripts](#clean-up-scripts) to help.
  * [Native setup steps](#native-prerequisites) - if you are using an Operating System such as Fedora, Centos or RHEL then you can install and run Docker natively and therefore OpenShift too
  * [Non native setup steps](#non-native-prerequisites) - if you are using an Operating System such as OSX or Windows then you'll need to use a Virtual Machine

### Getting started

We use a script which is downloaded via curl to configure and start OpenShift in a Docker container.  This script will also schedule a number of further containers such as the fabric8 console and optionally logging and metric dashboards in the form of [Kibana](http://www.elasticsearch.org/overview/kibana) and [Grafana](http://play.grafana.org/#/dashboard/db/grafana-play-home).

* [Start Scripts](#start-scripts)
* [Environment Variables](#environment-variables)
* [Deploy and Run a quickstart](example.html)
* [Using the OpenShift CLI (osc)](#using-the-kube-command-line)

---  


### Start Scripts

_The first time you run fabric8 v2 it may take some time as there are a number of docker images to download, this may require some patience_

If you are running fabric8 v2 for the first time we recommend you start with the basic script below


#### Basic

If you fancy starting OpenShift V3 the super-easy way which includes the [Fabric8 console](console.html) and a [Private Docker registry](https://registry.hub.docker.com/_/registry/), run this one-liner, once finished if a browser is installed it will open to the fabric8 console  

    bash <(curl -sSL https://bit.ly/get-fabric8)

#### Full

If you would like to try out a fully-featured installation, including aggregated logs & metrics, you can pass in the `-k` ("kitchen sink") flag __warning more images to download so longer to wait__

    bash <(curl -sSL https://bit.ly/get-fabric8) -k

#### Recreate

If you want to start from scratch, deleting all previously created Kubernetes containers, you can pass in the `-f` flag:

    bash <(curl -sSL https://bit.ly/get-fabric8) -f

#### Update

To update all the relevant images (corresponds to docker pull latest docker images), you can pass the `-u` flag:

    bash <(curl -sSL https://bit.ly/get-fabric8) -u

#### Combination

And of course flags can be combined, to start from scratch & update all images at once:

    bash <(curl -sSL https://bit.ly/get-fabric8) -kuf


---  

### Native prerequisites

1. First you'll need to [install docker](https://docs.docker.com/installation/), the later the version generally the better it is!
2. Ensure you enable sudoless use of the docker daemon for users that will run the start script


### Non Native prerequisites

Traditionally Mac and Windows users for example would use [boot2docker](http://boot2docker.io/) (a lightweight VM designed to make it feel like users can run docker natively).  This has been good in the past when working with a few containers however we have seen connectivity issues and problems related to low resources when working with more demanding technologies such as Kubernetes.  We __do not__ recommend you use boot2docker to run OpenShift.  

__Note: boot2docker is still required if you want to develop containers on your host.  You will need to build and push images which requires the Docker daemon and also if you want to use the OpenShift binary to interact with the [OpenShift CLI](#using-the-kube-command-line).__

We will use a VM to run OpenShift, you can configure a VM yourself, some use VMWare over Virtual Box and follow the Native steps above.  You will still need to add network routes as described in the steps below so you can access [Sevices](services.html) and [Pods](pods.html).

__If you want to avoid this then the simplest way to get going is to use the fabric8 VagrantFile:__

1. First you'll need to [install Docker](https://docs.docker.com/installation/) as mentioned above to interact with docker registries etc, the later the version generally the better it is!
2. Install [Vagrant](http://www.vagrantup.com/downloads.html)
3. Get the fabrc8 VM

    `git clone git@github.com:fabric8io/fabric8.git`  
    `cd fabric8`  
    `vagrant up`  
    `vagrant ssh`    


4. It's a good idea prime prime the docker registry (docker pull the images we will be using) and use Vagrant to take a snapshot so we can revert back to a clean start without re-downloading gigabites of docker images.

    `bash <(curl -sSL https://bit.ly/get-fabric8) -p`  

    or if you want to pull down all the docker images for the kitchen sink

    `bash <(curl -sSL https://bit.ly/get-fabric8) -pk`  

    take a snapshot of the vagrant image:

    `exit`  
    `vagrant snapshot take default cleanstart`  

    __Now at any point you can reset to the cleanstart snapshot via:__

    `vagrant snapshot go default cleanstart`


5.  Networking - Making sure you can access the IP addresses of services and pods from your host, you will need to add network routes on your host to access the fabric8 console and other services.  

    Set the **DOCKER_IP** environment variable.  This points to the IP address where docker is running.

    If you are using the fabric8 vagrant image then run:

    `export DOCKER_IP=172.28.128.4`

    If you are running with VM and have set it up yourself rather than using the Vagrant image then get the correct en* ipaddress you use to connect to your VM using `ip addr show` or `ifconfig` 

    `export DOCKER_IP=[correct en IP]`

    Then you should be able to add a network route so you can see the 172.X.X.X IP addresses:

    `sudo route -n delete 172.0.0.0/8`  
    `sudo route -n add 172.0.0.0/8 $DOCKER_IP`

    you should now be able to access IP addresses within kubernetes starting with "172."


### Environment variables

You'll need the following environment variables to be able use the [Tools](http://fabric8.io/v2/tools.html) such as the [Console](console.html), [Maven Plugin](http://fabric8.io/v2/mavenPlugin.html), the [Forge Addons](http://fabric8.io/v2/forge.html) and the [java libraries](javaLibraries.html):

If running Docker natively

    export DOCKER_IP=localhost
    export KUBERNETES_TRUST_CERT=true

If using a VM then use the machine IP, example below is for the fabric8 Vagrant image

    export DOCKER_IP=172.28.128.4
    export KUBERNETES_TRUST_CERT=true

The following are presented to you after the start script finishes and includes IP addresses of Services we intend to interact with..

    KUBERNETES_MASTER  
    DOCKER_REGISTRY  
    FABRIC8_CONSOLE  

### Using the kube command line

Run this command or add it to your ~/.bashrc

Natively  

    alias osc="docker run --rm -i --entrypoint=osc --net=host openshift/origin:latest --insecure-skip-tls-verify"

Non-natively  

    alias osc="docker run --rm -i -e KUBERNETES_MASTER=https://$DOCKER_IP:8443 --entrypoint=osc --net=host openshift/origin:latest --insecure-skip-tls-verify"

You can now use the kube command line to list pods, replication controllers and services; delete or create resources etc:

    osc get pods
    osc get replicationControllers
    osc get services

To see all the available commands:

    osc --help

**Note** that since we are using docker and you are typically running the docker commands from the host machine (your laptop), the osc command which itself runs in a linux container (on Windows or a Mac this is inside the boot2docker VM) it won't be able to access local files by file name when supplying -c to apply.

However you can pipe them into the command line via

    cat mything.json | osc create -f -

### Clean up scripts

To remove untagged images

    sudo docker rmi $( docker images | grep '<none>' | tr -s ' ' | cut -d ' ' -f 3)

Sometimes Docker containers are not removed completely.  The lines below will stop and remove OpenShift and all containers created by Kubernetes.

    docker kill openshift
    docker rm -v openshift
    docker kill $(docker ps -a | grep k8s | cut -c 1-12)
    docker rm -v $(docker ps -a -q)

### Running a hawtio console
If you are developing and working with hawtio you might want to run a locally built hawtio docker image against OpenShift..

    docker run -p 8484:8080 -it -e KUBERNETES_MASTER=https://$DOCKER_IP:8443 fabric8/hawtio

You can now access the web console at http://$DOCKER_IP:8484/hawtio/kubernetes/pods.
